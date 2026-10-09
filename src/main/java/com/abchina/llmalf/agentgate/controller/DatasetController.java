package com.abchina.llmalf.agentgate.controller;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.common.ApiErrors;
import com.abchina.llmalf.agentgate.common.ResponseBase;
import com.abchina.llmalf.agentgate.common.SafeMessages;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.dataset.Dataset;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.service.IDatasetService;
import com.abchina.llmalf.agentgate.service.format.DatasetXlsxFormat;
import com.abchina.llmalf.agentgate.service.format.XlsxFormatException;
import com.abchina.llmalf.agentgate.service.format.XlsxIssue;
import com.abchina.llmalf.agentgate.service.vo.CopyDatasetRequest;
import com.abchina.llmalf.agentgate.service.vo.CreateDatasetRequest;
import com.abchina.llmalf.agentgate.service.vo.CreateDraftRequest;
import com.abchina.llmalf.agentgate.service.vo.ReorderCasesRequest;
import com.abchina.llmalf.agentgate.service.vo.UpdateDatasetRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据集管理端点(核心,不含导入导出).
 *
 * <p>对齐 Python server/routes/datasets.py 的 16 个非 I/O 端点;
 * 404/422 映射逐端点对齐 raise_not_found/raise_unprocessable;
 * 响应为领域 toPayload 树(等价 pydantic model_dump)。</p>
 */
@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final IDatasetService datasetService;

    public DatasetController(IDatasetService datasetService) {
        this.datasetService = datasetService;
    }

    /**
     * 数据集摘要列表.
     *
     * @return 摘要列表
     */
    @GetMapping
    public ResponseBase<List<Map<String, Object>>> listDatasets() {
        return ResponseBase.success(datasetService.listDatasetSummaries(false));
    }

    /**
     * 创建数据集(连带初始草稿).
     *
     * @param request 创建请求
     * @return 数据集与草稿
     */
    @PostMapping
    public ResponseEntity<ResponseBase<Map<String, Object>>> createDataset(
            @Valid @RequestBody CreateDatasetRequest request) {
        Map<String, Object> result = ApiErrors.unprocessable(() -> {
            Dataset dataset = datasetService.createDataset(request.getName(),
                    request.getDescription());
            DatasetVersion draft = datasetService.createDraft(dataset.id(), null);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("dataset", dataset.toPayload());
            payload.put("draft", draft.toPayload());
            return payload;
        });
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseBase.success(result));
    }

    /**
     * 数据集详情(目录 + 全部版本).
     *
     * @param datasetId 数据集 id
     * @return 目录与版本
     */
    @GetMapping("/{datasetId}")
    public ResponseBase<Map<String, Object>> datasetDetail(
            @PathVariable("datasetId") String datasetId) {
        Map<String, Object> result = ApiErrors.notFound(() -> {
            Dataset dataset = datasetService.getDataset(datasetId);
            List<DatasetVersion> versions = datasetService.listVersions(datasetId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("dataset", dataset.toPayload());
            List<Object> versionPayloads = new ArrayList<>();
            for (DatasetVersion version : versions) {
                versionPayloads.add(version.toPayload());
            }
            payload.put("versions", versionPayloads);
            return payload;
        });
        return ResponseBase.success(result);
    }

    /**
     * 更新数据集元数据.
     *
     * @param datasetId 数据集 id
     * @param request 更新请求
     * @return 更新后数据集
     */
    @PatchMapping("/{datasetId}")
    public ResponseBase<Map<String, Object>> updateDataset(
            @PathVariable("datasetId") String datasetId,
            @Valid @RequestBody UpdateDatasetRequest request) {
        Map<String, Object> result = ApiErrors.unprocessable(() -> {
            Dataset updated = datasetService.updateDataset(datasetId, request.getName(),
                    request.getDescription(), request.getArchived());
            return (Map<String, Object>) updated.toPayload();
        });
        return ResponseBase.success(result);
    }

    /**
     * 归档数据集.
     *
     * @param datasetId 数据集 id
     * @return 归档后数据集
     */
    @DeleteMapping("/{datasetId}")
    public ResponseBase<Map<String, Object>> archiveDataset(
            @PathVariable("datasetId") String datasetId) {
        Map<String, Object> result = ApiErrors.notFound(() ->
                (Map<String, Object>) datasetService.archiveDataset(datasetId).toPayload());
        return ResponseBase.success(result);
    }

    /**
     * 复制数据集.
     *
     * @param datasetId 源数据集 id
     * @param request 复制请求
     * @return 新数据集与草稿
     */
    @PostMapping("/{datasetId}/copy")
    public ResponseEntity<ResponseBase<Map<String, Object>>> copyDataset(
            @PathVariable("datasetId") String datasetId,
            @Valid @RequestBody CopyDatasetRequest request) {
        Map<String, Object> result = ApiErrors.unprocessable(() ->
                datasetService.copyDataset(datasetId, request.getName(),
                        request.getSourceVersion()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseBase.success(result));
    }

    /**
     * 版本列表.
     *
     * @param datasetId 数据集 id
     * @return 版本列表
     */
    @GetMapping("/{datasetId}/versions")
    public ResponseBase<List<Object>> listDatasetVersions(
            @PathVariable("datasetId") String datasetId) {
        List<Object> result = ApiErrors.notFound(() -> {
            List<Object> payloads = new ArrayList<>();
            for (DatasetVersion version : datasetService.listVersions(datasetId)) {
                payloads.add(version.toPayload());
            }
            return payloads;
        });
        return ResponseBase.success(result);
    }

    /**
     * 发布版本详情.
     *
     * @param datasetId 数据集 id
     * @param version 版本号
     * @return 版本
     */
    @GetMapping("/{datasetId}/versions/{version}")
    public ResponseBase<Object> datasetVersion(@PathVariable("datasetId") String datasetId,
            @PathVariable("version") int version) {
        Object result = ApiErrors.notFound(() ->
                datasetService.getVersion(datasetId, version).toPayload());
        return ResponseBase.success(result);
    }

    /**
     * 当前草稿.
     *
     * @param datasetId 数据集 id
     * @return 草稿
     */
    @GetMapping("/{datasetId}/drafts/current")
    public ResponseBase<Object> currentDraft(@PathVariable("datasetId") String datasetId) {
        Object result = ApiErrors.notFound(() -> {
            DatasetVersion draft = datasetService.getDraft(datasetId);
            if (draft == null) {
                throw new IllegalArgumentException("Dataset has no active draft");
            }
            return draft.toPayload();
        });
        return ResponseBase.success(result);
    }

    /**
     * 创建草稿.
     *
     * @param datasetId 数据集 id
     * @param request 创建请求
     * @return 草稿
     */
    @PostMapping("/{datasetId}/drafts")
    public ResponseEntity<ResponseBase<Object>> createDraft(
            @PathVariable("datasetId") String datasetId,
            @RequestBody CreateDraftRequest request) {
        Object result = ApiErrors.unprocessable(() -> datasetService
                .createDraft(datasetId, request == null ? null : request.getBasedOnVersion())
                .toPayload());
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseBase.success(result));
    }

    /**
     * 丢弃草稿.
     *
     * @param datasetId 数据集 id
     * @return 204
     */
    @DeleteMapping("/{datasetId}/drafts/current")
    public ResponseEntity<Void> discardDraft(@PathVariable("datasetId") String datasetId) {
        ApiErrors.notFound(() -> {
            datasetService.discardDraft(datasetId);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    /**
     * 发布草稿.
     *
     * @param datasetId 数据集 id
     * @return 发布版本
     */
    @PostMapping("/{datasetId}/drafts/publish")
    public ResponseBase<Object> publishDraft(@PathVariable("datasetId") String datasetId) {
        Object result = ApiErrors.unprocessable(() ->
                datasetService.publishDraft(datasetId).toPayload());
        return ResponseBase.success(result);
    }

    /**
     * 追加用例.
     *
     * @param datasetId 数据集 id
     * @param casePayload 用例 payload
     * @return 更新后草稿
     */
    @PostMapping("/{datasetId}/drafts/cases")
    public ResponseEntity<ResponseBase<Object>> addCase(
            @PathVariable("datasetId") String datasetId,
            @RequestBody Map<String, Object> casePayload) {
        Object result = ApiErrors.unprocessable(() -> {
            Case caseItem = Case.fromPayload(casePayload);
            return datasetService.saveCase(datasetId, caseItem).toPayload();
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseBase.success(result));
    }

    /**
     * 更新用例(身份不可变).
     *
     * @param datasetId 数据集 id
     * @param caseId 用例 id
     * @param casePayload 用例 payload
     * @return 更新后草稿
     */
    @PutMapping("/{datasetId}/drafts/cases/{caseId}")
    public ResponseBase<Object> updateCase(@PathVariable("datasetId") String datasetId,
            @PathVariable("caseId") String caseId,
            @RequestBody Map<String, Object> casePayload) {
        Object result = ApiErrors.unprocessable(() -> {
            Case caseItem = Case.fromPayload(casePayload);
            if (!caseItem.id().equals(caseId)) {
                throw new IllegalArgumentException("Case ID cannot be changed");
            }
            return datasetService.saveCase(datasetId, caseItem).toPayload();
        });
        return ResponseBase.success(result);
    }

    /**
     * 移除用例.
     *
     * @param datasetId 数据集 id
     * @param caseId 用例 id
     * @return 更新后草稿
     */
    @DeleteMapping("/{datasetId}/drafts/cases/{caseId}")
    public ResponseBase<Object> deleteCase(@PathVariable("datasetId") String datasetId,
            @PathVariable("caseId") String caseId) {
        Object result = ApiErrors.notFound(() ->
                datasetService.removeCase(datasetId, caseId).toPayload());
        return ResponseBase.success(result);
    }

    /**
     * 复制用例.
     *
     * @param datasetId 数据集 id
     * @param caseId 用例 id
     * @return 更新后草稿
     */
    @PostMapping("/{datasetId}/drafts/cases/{caseId}/copy")
    public ResponseBase<Object> copyCase(@PathVariable("datasetId") String datasetId,
            @PathVariable("caseId") String caseId) {
        Object result = ApiErrors.notFound(() ->
                datasetService.copyCase(datasetId, caseId).toPayload());
        return ResponseBase.success(result);
    }

    /**
     * 重排用例.
     *
     * @param datasetId 数据集 id
     * @param request 重排请求
     * @return 更新后草稿
     */
    @PutMapping("/{datasetId}/drafts/case-order")
    public ResponseBase<Object> reorderCases(@PathVariable("datasetId") String datasetId,
            @Valid @RequestBody ReorderCasesRequest request) {
        Object result = ApiErrors.unprocessable(() ->
                datasetService.reorderCases(datasetId, request.getCaseIds()).toPayload());
        return ResponseBase.success(result);
    }

    /**
     * 导入 JSON 交换文档.
     *
     * @param document 交换文档
     * @return 数据集与版本
     */
    @PostMapping("/import")
    public ResponseEntity<ResponseBase<Map<String, Object>>> importDataset(
            @RequestBody Map<String, Object> document) {
        Map<String, Object> result = ApiErrors.unprocessable(() ->
                datasetService.importJson(document));
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseBase.success(result));
    }

    /**
     * 导入 XLSX 用例表.
     *
     * @param file XLSX 文件
     * @param name 数据集名称
     * @param description 描述
     * @return 数据集与版本
     */
    @PostMapping("/import/xlsx")
    public ResponseEntity<ResponseBase<Map<String, Object>>> importDatasetXlsx(
            @RequestParam("file")
            MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "description",
                    required = false, defaultValue = "") String description) {
        if (file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            throw xlsxError(new XlsxIssue(
                    DatasetXlsxFormat.SHEET_NAME,
                    null, null, "file must have a .xlsx filename"));
        }
        if (file.getSize() > DatasetXlsxFormat
                .MAX_INPUT_BYTES) {
            throw xlsxError(new XlsxIssue(
                    DatasetXlsxFormat.SHEET_NAME,
                    null, null, "file exceeds the 10 MiB limit"));
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw xlsxError(new XlsxIssue(
                    DatasetXlsxFormat.SHEET_NAME,
                    null, null, "file is not a valid XLSX archive"));
        }
        Map<String, Object> result;
        try {
            result = datasetService.importXlsx(content, name, description);
        } catch (XlsxFormatException e) {
            throw xlsxError(e);
        } catch (IllegalArgumentException e) {
            throw new AgentException(422, e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseBase.success(result));
    }

    /**
     * 导出 JSON 交换文档.
     *
     * @param datasetId 数据集 id
     * @param version 版本号
     * @return 交换文档对象
     */
    @GetMapping("/{datasetId}/versions/{version}/export")
    public ResponseBase<Object> exportDatasetJson(@PathVariable("datasetId") String datasetId,
            @PathVariable("version") int version) {
        Object result = ApiErrors.notFound(() -> {
            IDatasetService.ExportedVersion exported =
                    datasetService.exportVersion(datasetId, version, "json");
            try {
                return new ObjectMapper().readTree(
                        exported.content());
            } catch (Exception e) {
                throw new IllegalStateException("exported JSON is invalid", e);
            }
        });
        return ResponseBase.success(result);
    }

    /**
     * 导出 XLSX 文件.
     *
     * @param datasetId 数据集 id
     * @param version 版本号
     * @return XLSX 文件流
     */
    @GetMapping("/{datasetId}/versions/{version}/export/xlsx")
    public ResponseEntity<byte[]> exportDatasetXlsx(@PathVariable("datasetId") String datasetId,
            @PathVariable("version") int version) {
        IDatasetService.ExportedVersion exported;
        try {
            exported = datasetService.exportVersion(datasetId, version, "xlsx");
        } catch (XlsxFormatException e) {
            throw xlsxError(e);
        } catch (IllegalArgumentException e) {
            throw new AgentException(404, e.getMessage());
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + exported.filename() + "\"")
                .header("ETag", "\"" + exported.contentSha256() + "\"")
                .header("Cache-Control", "private, immutable")
                .contentType(MediaType.parseMediaType(exported.mediaType()))
                .body(exported.content());
    }

    private static AgentException xlsxError(
            XlsxIssue... issues) {
        return xlsxError(new XlsxFormatException(
                Arrays.asList(issues)));
    }

    private static AgentException xlsxError(
            XlsxFormatException error) {
        List<Map<String, Object>> issueList = new ArrayList<>();
        for (XlsxIssue issue : error.issues()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sheet", issue.sheet());
            item.put("row", issue.row());
            item.put("column", issue.column());
            item.put("message", SafeMessages.redact(
                    issue.message()));
            issueList.add(item);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("code", "xlsx_validation_failed");
        detail.put("issue_count", issueList.size());
        detail.put("issues", issueList);
        return new AgentException(422, "xlsx_validation_failed", detail);
    }
}
