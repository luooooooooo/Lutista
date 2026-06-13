#pragma once

#include <vulkan/vulkan.h>

class VulkanDevice;

class VulkanCommandPool {
public:
    bool init(const VulkanDevice& device);
    void destroy(VkDevice device);

    VkCommandBuffer beginSingleTimeCommands(const VulkanDevice& device);
    void endSingleTimeCommands(const VulkanDevice& device, VkCommandBuffer cmd);

    VkCommandPool pool() const { return m_pool; }

private:
    VkCommandPool m_pool = VK_NULL_HANDLE;
    VkFence m_fence = VK_NULL_HANDLE;
};
