package com.abchina.llmalf.agentgate.service;

import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.dataset.Dataset;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;

import java.util.List;
import java.util.Map;

/**
 * 数据集管理服务.
 *
 * <p>对齐 Python application/dataset_management.py::DatasetManagement
 * (目录/版本/草稿/用例编辑/发布/复制;团队上下文注入)。</p>
 */
public interface IDatasetService {

    /**
     * 列出团队数据集摘要(updated_at 降序,附版本/用例数/草稿标记).
     *
     * @param includeArchived 是否包含归档
     * @return 摘要列表
     */
    List<Map<String, Object>> listDatasetSummaries(boolean includeArchived);

    /**
     * 查询数据集(他队或不存在 → 域异常).
     *
     * @param datasetId 数据集 id
     * @return 数据集
     */
    Dataset getDataset(String datasetId);

    /**
     * 创建数据集(名称/描述 trim,注入当前用户上下文).
     *
     * @param name 名称
     * @param description 描述
     * @return 数据集
     */
    Dataset createDataset(String name, String description);

    /**
     * 更新数据集(可选字段覆写,时间刷新).
     *
     * @param datasetId 数据集 id
     * @param name 名称(可空)
     * @param description 描述(可空)
     * @param archived 归档(可空)
     * @return 更新后数据集
     */
    Dataset updateDataset(String datasetId, String name, String description, Boolean archived);

    /**
     * 归档数据集.
     *
     * @param datasetId 数据集 id
     * @return 更新后数据集
     */
    Dataset archiveDataset(String datasetId);

    /**
     * 列出版本(草稿优先,同组版本降序).
     *
     * @param datasetId 数据集 id
     * @return 版本列表
     */
    List<DatasetVersion> listVersions(String datasetId);

    /**
     * 查询发布版本.
     *
     * @param datasetId 数据集 id
     * @param version 版本号
     * @return 版本
     */
    DatasetVersion getVersion(String datasetId, int version);

    /**
     * 查询当前草稿.
     *
     * @param datasetId 数据集 id
     * @return 草稿(可空)
     */
    DatasetVersion getDraft(String datasetId);

    /**
     * 创建草稿(默认基于最新发布版).
     *
     * @param datasetId 数据集 id
     * @param basedOnVersion 基线版本(可空)
     * @return 草稿
     */
    DatasetVersion createDraft(String datasetId, Integer basedOnVersion);

    /**
     * 丢弃草稿.
     *
     * @param datasetId 数据集 id
     */
    void discardDraft(String datasetId);

    /**
     * 追加或替换用例.
     *
     * @param datasetId 数据集 id
     * @param caseItem 用例
     * @return 更新后草稿
     */
    DatasetVersion saveCase(String datasetId, Case caseItem);

    /**
     * 移除用例.
     *
     * @param datasetId 数据集 id
     * @param caseId 用例 id
     * @return 更新后草稿
     */
    DatasetVersion removeCase(String datasetId, String caseId);

    /**
     * 复制用例(新 id + 副本名).
     *
     * @param datasetId 数据集 id
     * @param caseId 用例 id
     * @return 更新后草稿
     */
    DatasetVersion copyCase(String datasetId, String caseId);

    /**
     * 重排用例.
     *
     * @param datasetId 数据集 id
     * @param caseIds 用例 id 完整序列
     * @return 更新后草稿
     */
    DatasetVersion reorderCases(String datasetId, List<String> caseIds);

    /**
     * 发布草稿.
     *
     * @param datasetId 数据集 id
     * @return 发布版本
     */
    DatasetVersion publishDraft(String datasetId);

    /**
     * 导入 JSON 交换文档(envelope 校验 + 身份匹配 + 用户覆写 + 存在性检查).
     *
     * @param document 交换文档
     * @return 数据集与版本 payload
     */
    Map<String, Object> importJson(Map<String, Object> document);

    /**
     * 导入 XLSX 用例表(解析 → 新数据集 + 草稿).
     *
     * @param content XLSX 字节
     * @param name 数据集名称
     * @param description 描述
     * @return 数据集与版本 payload
     */
    Map<String, Object> importXlsx(byte[] content, String name, String description);

    /**
     * 导出版本(json/xlsx).
     *
     * @param datasetId 数据集 id
     * @param version 版本号
     * @param formatName json 或 xlsx
     * @return 编码结果(content/mediaType/filename)
     */
    ExportedVersion exportVersion(String datasetId, int version, String formatName);

    /**
     * 导出结果载体.
     */
    final class ExportedVersion {

        private final byte[] content;
        private final String mediaType;
        private final String filename;
        private final String contentSha256;

        public ExportedVersion(byte[] content, String mediaType, String filename,
                String contentSha256) {
            this.content = content;
            this.mediaType = mediaType;
            this.filename = filename;
            this.contentSha256 = contentSha256;
        }

        public byte[] content() {
            return content;
        }

        public String mediaType() {
            return mediaType;
        }

        public String filename() {
            return filename;
        }

        public String contentSha256() {
            return contentSha256;
        }
    }

    /**
     * 复制数据集(源版本内容 → 新数据集 + 草稿).
     *
     * @param sourceDatasetId 源数据集 id
     * @param name 新名称
     * @param sourceVersion 源版本(可空,默认最新发布版)
     * @return 数据集与草稿
     */
    Map<String, Object> copyDataset(String sourceDatasetId, String name, Integer sourceVersion);
}
