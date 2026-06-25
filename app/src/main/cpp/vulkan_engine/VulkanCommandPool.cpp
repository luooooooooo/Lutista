#include "VulkanCommandPool.h"
#include "VulkanDevice.h"
#include <android/log.h>

#define LOG_TAG "VulkanCmdPool"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

bool VulkanCommandPool::init(const VulkanDevice& device) {
    VkCommandPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolInfo.queueFamilyIndex = device.computeQueueFamily();

    if (vkCreateCommandPool(device.device(), &poolInfo, nullptr, &m_pool) != VK_SUCCESS) {
        LOGE("Failed to create command pool");
        return false;
    }

    VkFenceCreateInfo fenceInfo{};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;

    if (vkCreateFence(device.device(), &fenceInfo, nullptr, &m_fence) != VK_SUCCESS) {
        LOGE("Failed to create fence");
        return false;
    }

    return true;
}

void VulkanCommandPool::destroy(VkDevice device) {
    if (m_fence != VK_NULL_HANDLE) {
        vkDestroyFence(device, m_fence, nullptr);
        m_fence = VK_NULL_HANDLE;
    }
    if (m_pool != VK_NULL_HANDLE) {
        vkDestroyCommandPool(device, m_pool, nullptr);
        m_pool = VK_NULL_HANDLE;
    }
}

VkCommandBuffer VulkanCommandPool::beginSingleTimeCommands(const VulkanDevice& device) {
    VkCommandBufferAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    allocInfo.commandPool = m_pool;
    allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    allocInfo.commandBufferCount = 1;

    VkCommandBuffer cmd;
    if (vkAllocateCommandBuffers(device.device(), &allocInfo, &cmd) != VK_SUCCESS) {
        LOGE("Failed to allocate command buffer");
        return VK_NULL_HANDLE;
    }

    if (vkResetFences(device.device(), 1, &m_fence) != VK_SUCCESS) {
        LOGE("Failed to reset fence");
        vkFreeCommandBuffers(device.device(), m_pool, 1, &cmd);
        return VK_NULL_HANDLE;
    }

    VkCommandBufferBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    if (vkBeginCommandBuffer(cmd, &beginInfo) != VK_SUCCESS) {
        LOGE("Failed to begin command buffer");
        vkFreeCommandBuffers(device.device(), m_pool, 1, &cmd);
        return VK_NULL_HANDLE;
    }

    return cmd;
}

void VulkanCommandPool::endSingleTimeCommands(const VulkanDevice& device, VkCommandBuffer cmd) {
    if (vkEndCommandBuffer(cmd) != VK_SUCCESS) {
        LOGE("Failed to end command buffer");
        vkFreeCommandBuffers(device.device(), m_pool, 1, &cmd);
        return;
    }

    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &cmd;

    VkResult result = vkQueueSubmit(device.computeQueue(), 1, &submitInfo, m_fence);
    if (result != VK_SUCCESS) {
        LOGE("Queue submit failed: %d", result);
        vkFreeCommandBuffers(device.device(), m_pool, 1, &cmd);
        return;
    }

    result = vkWaitForFences(device.device(), 1, &m_fence, VK_TRUE, UINT64_MAX);
    if (result != VK_SUCCESS) {
        LOGE("Wait for fences failed: %d", result);
    }

    vkFreeCommandBuffers(device.device(), m_pool, 1, &cmd);
}
