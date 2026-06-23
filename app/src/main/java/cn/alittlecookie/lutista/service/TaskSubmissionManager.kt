package cn.alittlecookie.lutista.service

import cn.alittlecookie.lutista.data.database.entity.TaskEntity
import cn.alittlecookie.lutista.data.database.entity.TaskSource
import cn.alittlecookie.lutista.data.database.entity.TaskStatus
import cn.alittlecookie.lutista.data.repository.TaskRepository
import cn.alittlecookie.lutista.manager.LogManager
import cn.alittlecookie.lutista.manager.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class TaskSubmissionManager(
    private val taskRepository: TaskRepository,
    private val imageProcessingService: ImageProcessingService,
    private val logger: LogManager,
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "TaskSubmissionManager"
        private const val MAX_RETRY_COUNT = 2
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val taskQueue = java.util.PriorityQueue<TaskEntity>(compareBy { it.priority })
    private val mutex = Mutex()

    val cancelFlagMap = ConcurrentHashMap<Long, AtomicBoolean>()
    private val pauseFlag = AtomicBoolean(false)
    private val isProcessing = AtomicBoolean(false)

    suspend fun submitTask(
        sourcePath: String,
        source: TaskSource = TaskSource.MANUAL,
        filterId: Long? = null,
        watermarkId: Long? = null,
        outputPath: String = ""
    ): Long {
        val task = TaskEntity(
            sourcePath = sourcePath,
            outputPath = outputPath,
            filterId = filterId,
            watermarkId = watermarkId,
            status = TaskStatus.PENDING.name,
            priority = if (source == TaskSource.MANUAL) 0 else 1,
            source = source.name
        )

        val taskId = taskRepository.insert(task)
        val taskWithId = task.copy(id = taskId)

        mutex.withLock {
            taskQueue.add(taskWithId)
        }

        cancelFlagMap[taskId] = AtomicBoolean(false)

        logger.i(TAG, "任务已提交: $taskId, 来源: $source, 路径: $sourcePath")

        if (!isProcessing.get()) {
            startProcessing()
        }

        return taskId
    }

    private fun startProcessing() {
        if (!isProcessing.compareAndSet(false, true)) return

        scope.launch {
            while (isProcessing.get()) {
                while (pauseFlag.get()) {
                    delay(100)
                }

                val task = mutex.withLock { taskQueue.poll() } ?: run {
                    isProcessing.set(false)
                    return@launch
                }

                if (isTaskCancelled(task.id)) {
                    taskRepository.updateStatus(task.id, TaskStatus.CANCELLED.name)
                    cancelFlagMap.remove(task.id)
                    continue
                }

                var retryCount = 0
                var success = false

                while (retryCount <= MAX_RETRY_COUNT && !success) {
                    try {
                        taskRepository.updateStatus(task.id, TaskStatus.PROCESSING.name)

                        imageProcessingService.processImage(task) { progress ->
                            if (isTaskCancelled(task.id)) {
                                throw CancellationException("任务被取消")
                            }
                            scope.launch {
                                taskRepository.updateProgress(task.id, progress)
                            }
                        }

                        success = true
                        taskRepository.completeTask(task.id, TaskStatus.COMPLETED.name)
                        settingsManager.incrementProcessedCount()
                        logger.i(TAG, "任务完成: ${task.id}")
                    } catch (e: CancellationException) {
                        taskRepository.updateStatus(task.id, TaskStatus.CANCELLED.name)
                        logger.i(TAG, "任务被取消: ${task.id}")
                        break
                    } catch (e: Exception) {
                        retryCount++
                        logger.w(TAG, "任务失败 (重试 $retryCount/$MAX_RETRY_COUNT): ${task.id}", e)

                        if (retryCount <= MAX_RETRY_COUNT) {
                            taskRepository.updateStatus(task.id, TaskStatus.RETRYING.name)
                            taskRepository.updateRetryCount(task.id, retryCount)
                            delay(1000L * retryCount)
                        } else {
                            taskRepository.updateStatus(task.id, TaskStatus.FAILED.name)
                            taskRepository.updateError(task.id, e.message ?: "未知错误")
                            logger.e(TAG, "任务最终失败，已跳过: ${task.id}", e)
                        }
                    }
                }

                cancelFlagMap.remove(task.id)
            }
        }
    }

    fun cancelTask(taskId: Long) {
        cancelFlagMap[taskId]?.set(true)
        logger.i(TAG, "取消任务: $taskId")
    }

    fun pauseQueue() {
        pauseFlag.set(true)
        logger.i(TAG, "队列已暂停")
    }

    fun resumeQueue() {
        pauseFlag.set(false)
        logger.i(TAG, "队列已恢复")
    }

    private fun isTaskCancelled(taskId: Long): Boolean {
        return cancelFlagMap[taskId]?.get() ?: false
    }

    suspend fun retryTask(taskId: Long) {
        val task = taskRepository.getTaskById(taskId) ?: return

        if (task.status == TaskStatus.FAILED.name) {
            taskRepository.updateStatus(taskId, TaskStatus.PENDING.name)
            cancelFlagMap[taskId] = AtomicBoolean(false)

            mutex.withLock {
                taskQueue.add(task.copy(status = TaskStatus.PENDING.name))
            }

            if (!isProcessing.get()) {
                startProcessing()
            }
        }
    }
}
