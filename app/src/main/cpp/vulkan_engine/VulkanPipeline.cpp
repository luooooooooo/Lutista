#include "VulkanPipeline.h"
#include "VulkanDevice.h"
#include <fstream>
#include <android/log.h>

#define LOG_TAG "VulkanPipeline"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

bool VulkanPipeline::createComputePipeline(const VulkanDevice& device,
                                            const std::vector<uint8_t>& spirvCode,
                                            VkDescriptorSetLayout descriptorSetLayout,
                                            uint32_t pushConstantSize) {
    VkShaderModuleCreateInfo moduleInfo{};
    moduleInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    moduleInfo.codeSize = spirvCode.size();
    moduleInfo.pCode = reinterpret_cast<const uint32_t*>(spirvCode.data());

    VkShaderModule shaderModule;
    if (vkCreateShaderModule(device.device(), &moduleInfo, nullptr, &shaderModule) != VK_SUCCESS) {
        LOGE("Failed to create shader module");
        return false;
    }

    VkPipelineShaderStageCreateInfo stageInfo{};
    stageInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stageInfo.stage = VK_SHADER_STAGE_COMPUTE_BIT;
    stageInfo.module = shaderModule;
    stageInfo.pName = "main";

    VkPipelineLayoutCreateInfo layoutInfo{};
    layoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    layoutInfo.setLayoutCount = 1;
    layoutInfo.pSetLayouts = &descriptorSetLayout;

    VkPushConstantRange pushRange{};
    if (pushConstantSize > 0) {
        pushRange.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
        pushRange.offset = 0;
        pushRange.size = pushConstantSize;
        layoutInfo.pushConstantRangeCount = 1;
        layoutInfo.pPushConstantRanges = &pushRange;
    }

    if (vkCreatePipelineLayout(device.device(), &layoutInfo, nullptr, &m_pipelineLayout) != VK_SUCCESS) {
        LOGE("Failed to create pipeline layout");
        vkDestroyShaderModule(device.device(), shaderModule, nullptr);
        return false;
    }

    VkComputePipelineCreateInfo pipelineInfo{};
    pipelineInfo.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
    pipelineInfo.stage = stageInfo;
    pipelineInfo.layout = m_pipelineLayout;

    VkResult result = vkCreateComputePipelines(device.device(), VK_NULL_HANDLE, 1, &pipelineInfo, nullptr, &m_pipeline);
    vkDestroyShaderModule(device.device(), shaderModule, nullptr);

    if (result != VK_SUCCESS) {
        LOGE("Failed to create compute pipeline");
        return false;
    }

    return true;
}

void VulkanPipeline::destroy(VkDevice device) {
    if (m_pipeline != VK_NULL_HANDLE) {
        vkDestroyPipeline(device, m_pipeline, nullptr);
        m_pipeline = VK_NULL_HANDLE;
    }
    if (m_pipelineLayout != VK_NULL_HANDLE) {
        vkDestroyPipelineLayout(device, m_pipelineLayout, nullptr);
        m_pipelineLayout = VK_NULL_HANDLE;
    }
}

std::vector<uint8_t> VulkanPipeline::loadSpirV(const std::string& path) {
    std::ifstream file(path, std::ios::ate | std::ios::binary);
    if (!file.is_open()) {
        LOGE("Failed to open SPIR-V file: %s", path.c_str());
        return {};
    }

    size_t fileSize = static_cast<size_t>(file.tellg());
    std::vector<uint8_t> buffer(fileSize);
    file.seekg(0);
    file.read(reinterpret_cast<char*>(buffer.data()), fileSize);
    return buffer;
}
