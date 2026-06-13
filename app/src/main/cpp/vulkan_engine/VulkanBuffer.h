#pragma once

#include <vulkan/vulkan.h>
#include <vector>

class VulkanDevice;

class VulkanBuffer {
public:
    bool create(const VulkanDevice& device, VkDeviceSize size,
                VkBufferUsageFlags usage, VkMemoryPropertyFlags properties);
    void destroy(VkDevice device);

    VkBuffer buffer() const { return m_buffer; }
    VkDeviceMemory memory() const { return m_memory; }
    VkDeviceSize size() const { return m_size; }

    bool upload(VkDevice device, const void* data, VkDeviceSize dataSize);
    bool download(VkDevice device, void* data, VkDeviceSize dataSize);

private:
    VkBuffer m_buffer = VK_NULL_HANDLE;
    VkDeviceMemory m_memory = VK_NULL_HANDLE;
    VkDeviceSize m_size = 0;
};
