#pragma once

#include <vulkan/vulkan.h>
#include <vector>
#include <string>
#include <unordered_map>

class VulkanDevice;

class VulkanPipeline {
public:
    bool createComputePipeline(const VulkanDevice& device,
                               const std::vector<uint8_t>& spirvCode,
                               VkDescriptorSetLayout descriptorSetLayout,
                               uint32_t pushConstantSize);
    void destroy(VkDevice device);

    VkPipeline pipeline() const { return m_pipeline; }
    VkPipelineLayout layout() const { return m_pipelineLayout; }

    static std::vector<uint8_t> loadSpirV(const std::string& path);

private:
    VkPipeline m_pipeline = VK_NULL_HANDLE;
    VkPipelineLayout m_pipelineLayout = VK_NULL_HANDLE;
};
