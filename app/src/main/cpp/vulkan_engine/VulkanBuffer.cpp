#include "VulkanBuffer.h"
#include "VulkanDevice.h"
#include <cstring>
#include <android/log.h>

#define LOG_TAG "VulkanBuffer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

bool VulkanBuffer::create(const VulkanDevice& device, VkDeviceSize size,
                          VkBufferUsageFlags usage, VkMemoryPropertyFlags properties) {
    m_size = size;

    VkBufferCreateInfo bufferInfo{};
    bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufferInfo.size = size;
    bufferInfo.usage = usage;
    bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    if (vkCreateBuffer(device.device(), &bufferInfo, nullptr, &m_buffer) != VK_SUCCESS) {
        LOGE("Failed to create buffer (size=%zu)", size);
        return false;
    }

    VkMemoryRequirements memReqs;
    vkGetBufferMemoryRequirements(device.device(), m_buffer, &memReqs);

    VkMemoryAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = device.findMemoryType(memReqs.memoryTypeBits, properties);

    if (allocInfo.memoryTypeIndex == UINT32_MAX) {
        LOGE("No suitable memory type for buffer (size=%zu)", size);
        vkDestroyBuffer(device.device(), m_buffer, nullptr);
        m_buffer = VK_NULL_HANDLE;
        return false;
    }

    VkResult result = vkAllocateMemory(device.device(), &allocInfo, nullptr, &m_memory);
    if (result != VK_SUCCESS) {
        LOGE("Failed to allocate buffer memory: %d (requested %zu bytes)", result, size);
        vkDestroyBuffer(device.device(), m_buffer, nullptr);
        m_buffer = VK_NULL_HANDLE;
        return false;
    }

    if (vkBindBufferMemory(device.device(), m_buffer, m_memory, 0) != VK_SUCCESS) {
        LOGE("Failed to bind buffer memory");
        vkFreeMemory(device.device(), m_memory, nullptr);
        vkDestroyBuffer(device.device(), m_buffer, nullptr);
        m_memory = VK_NULL_HANDLE;
        m_buffer = VK_NULL_HANDLE;
        return false;
    }

    LOGI("Buffer created: %zu bytes", size);
    return true;
}

void VulkanBuffer::destroy(VkDevice device) {
    if (m_buffer != VK_NULL_HANDLE) {
        vkDestroyBuffer(device, m_buffer, nullptr);
        m_buffer = VK_NULL_HANDLE;
    }
    if (m_memory != VK_NULL_HANDLE) {
        vkFreeMemory(device, m_memory, nullptr);
        m_memory = VK_NULL_HANDLE;
    }
}

bool VulkanBuffer::upload(VkDevice device, const void* data, VkDeviceSize dataSize) {
    if (m_memory == VK_NULL_HANDLE) {
        LOGE("upload: buffer not initialized");
        return false;
    }
    void* mapped = nullptr;
    VkResult result = vkMapMemory(device, m_memory, 0, dataSize, 0, &mapped);
    if (result != VK_SUCCESS || mapped == nullptr) {
        LOGE("upload: vkMapMemory failed: %d (size=%zu)", result, dataSize);
        return false;
    }
    memcpy(mapped, data, dataSize);
    vkUnmapMemory(device, m_memory);
    return true;
}

bool VulkanBuffer::download(VkDevice device, void* data, VkDeviceSize dataSize) {
    if (m_memory == VK_NULL_HANDLE) {
        LOGE("download: buffer not initialized");
        return false;
    }
    void* mapped = nullptr;
    VkResult result = vkMapMemory(device, m_memory, 0, dataSize, 0, &mapped);
    if (result != VK_SUCCESS || mapped == nullptr) {
        LOGE("download: vkMapMemory failed: %d (size=%zu)", result, dataSize);
        return false;
    }
    memcpy(data, mapped, dataSize);
    vkUnmapMemory(device, m_memory);
    return true;
}
