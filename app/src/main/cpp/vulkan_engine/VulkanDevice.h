#pragma once

#include <vulkan/vulkan.h>
#include <vector>
#include <string>
#include <cstdint>

class VulkanDevice {
public:
    bool init();
    void destroy();
    bool isAvailable() const { return m_device != VK_NULL_HANDLE; }

    VkDevice device() const { return m_device; }
    VkPhysicalDevice physicalDevice() const { return m_physicalDevice; }
    VkQueue computeQueue() const { return m_computeQueue; }
    uint32_t computeQueueFamily() const { return m_computeQueueFamily; }
    const VkPhysicalDeviceMemoryProperties& memoryProperties() const { return m_memoryProperties; }
    const VkPhysicalDeviceLimits& limits() const { return m_limits; }

    uint32_t findMemoryType(uint32_t typeFilter, VkMemoryPropertyFlags properties) const;

private:
    VkInstance m_instance = VK_NULL_HANDLE;
    VkPhysicalDevice m_physicalDevice = VK_NULL_HANDLE;
    VkDevice m_device = VK_NULL_HANDLE;
    VkQueue m_computeQueue = VK_NULL_HANDLE;
    uint32_t m_computeQueueFamily = 0;
    VkPhysicalDeviceMemoryProperties m_memoryProperties{};
    VkPhysicalDeviceLimits m_limits{};

    bool createInstance();
    bool selectPhysicalDevice();
    bool createLogicalDevice();
};
