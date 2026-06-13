#pragma once

#include <vulkan/vulkan.h>

class VulkanDevice;

class VulkanImage {
public:
    bool createStorageImage2D(const VulkanDevice& device, uint32_t width, uint32_t height,
                              VkFormat format = VK_FORMAT_R32G32B32A32_SFLOAT);
    bool createSampledImage3D(const VulkanDevice& device, uint32_t width, uint32_t height, uint32_t depth,
                              VkFormat format = VK_FORMAT_R32G32B32A32_SFLOAT);
    void destroy(VkDevice device);

    VkImage image() const { return m_image; }
    VkImageView imageView() const { return m_imageView; }
    VkSampler sampler() const { return m_sampler; }
    VkDeviceMemory memory() const { return m_memory; }
    VkFormat format() const { return m_format; }

    void transitionLayout(VkCommandBuffer cmd, VkImageLayout oldLayout, VkImageLayout newLayout);

private:
    VkImage m_image = VK_NULL_HANDLE;
    VkImageView m_imageView = VK_NULL_HANDLE;
    VkSampler m_sampler = VK_NULL_HANDLE;
    VkDeviceMemory m_memory = VK_NULL_HANDLE;
    VkFormat m_format = VK_FORMAT_UNDEFINED;

    bool allocateMemory(const VulkanDevice& device, VkMemoryPropertyFlags properties);
};
