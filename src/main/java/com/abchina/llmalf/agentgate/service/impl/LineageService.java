package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.common.UserContextHolder;
import com.abchina.llmalf.agentgate.domain.ContentSha256;
import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.abchina.llmalf.agentgate.domain.model.target.SkillDescriptor;
import com.abchina.llmalf.agentgate.domain.model.target.TargetDescriptor;
import com.abchina.llmalf.agentgate.domain.model.target.TargetRef;
import com.abchina.llmalf.agentgate.domain.model.target.TargetType;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import com.abchina.llmalf.agentgate.logic.TargetLogic;
import com.abchina.llmalf.agentgate.logic.DatasetLogic;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 血缘查询服务.
 *
 * <p>对齐 Python application/lineage_queries.py:6 个入口
 * (run/dataset/case/target/skill/evaluator)构建确定性血缘图;
 * 节点 id 为 `{kind}:{content_sha256}`,Run 节点为 `run:{run_id}`。</p>
 */
@Service
public class LineageService {

    private final RunLogic runLogic;
    private final DatasetLogic datasetLogic;
    private final TargetLogic targetLogic;

    public LineageService(RunLogic runLogic, DatasetLogic datasetLogic,
            TargetLogic targetLogic) {
        this.runLogic = runLogic;
        this.datasetLogic = datasetLogic;
        this.targetLogic = targetLogic;
    }

    /**
     * Run 血缘图.
     *
     * @param runId Run id
     * @return 图投影
     */
    public Map<String, Object> runLineage(String runId) {
        EvaluationRun run = requireRun(runId);
        TargetDescriptor descriptor = resolveDescriptor(run);
        return buildRunGraph(run, descriptor).payload();
    }

    /**
     * 数据集版本血缘图(反向关联 Run).
     *
     * @param datasetId 数据集 id
     * @param version 版本号
     * @param limit 上限
     * @return 图投影
     */
    public Map<String, Object> datasetLineage(String datasetId, int version, int limit) {
        DatasetVersion dataset = datasetLogic.getPublishedDatasetVersion(datasetId, version,
                teamId());
        if (dataset == null) {
            throw new AgentException(404,
                    "unknown published DatasetVersion: " + datasetId + " v" + version);
        }
        List<EvaluationRun> runs = runLogic.listRunsByDatasetVersion(datasetId, version,
                limit, teamId());
        return expandReverse(datasetNode(dataset), runs).payload();
    }

    /**
     * 用例血缘图(按用例内容反向关联 Run).
     *
     * @param datasetId 数据集 id
     * @param version 版本号
     * @param caseId 用例 id
     * @param limit 上限
     * @return 图投影
     */
    public Map<String, Object> caseLineage(String datasetId, int version, String caseId,
            int limit) {
        DatasetVersion dataset = datasetLogic.getPublishedDatasetVersion(datasetId, version,
                teamId());
        if (dataset == null) {
            throw new AgentException(404,
                    "unknown published DatasetVersion: " + datasetId + " v" + version);
        }
        Case target = null;
        for (Case item : dataset.cases()) {
            if (item.id().equals(caseId)) {
                target = item;
                break;
            }
        }
        if (target == null) {
            throw new AgentException(404, "unknown Case in DatasetVersion: " + caseId);
        }
        String caseHash = ContentSha256.of(target.toPayload());
        List<EvaluationRun> runs = runLogic.listRunsByCaseContent(datasetId, version,
                caseId, caseHash, limit, teamId());
        return expandReverse(caseNode(dataset, target), runs).payload();
    }

    /**
     * 目标血缘图.
     *
     * @param ref 目标身份
     * @param contentHash 内容摘要(可空)
     * @param limit 上限
     * @return 图投影
     */
    public Map<String, Object> targetLineage(TargetRef ref, String contentHash, int limit) {
        List<TargetDescriptor> descriptors = targetLogic.listTargetDescriptors(ref);
        TargetDescriptor descriptor = selectDescriptor(descriptors, contentHash);
        Node root = targetNode(descriptor);
        List<EvaluationRun> runs = runLogic.listRunsByTargetVersion(
                ref.sourceId(), ref.targetType().wireValue(), ref.externalTargetId(),
                ref.externalVersionId(), limit, descriptor.contentSha256(), teamId());
        return expandReverse(root, runs, true).payload();
    }

    /**
     * 技能血缘图.
     *
     * @param sourceId 来源 id
     * @param skillId 技能 id
     * @param version 技能版本
     * @param contentHash 内容摘要(可空)
     * @param limit 上限
     * @return 图投影
     */
    public Map<String, Object> skillLineage(String sourceId, String skillId, String version,
            String contentHash, int limit) {
        Map<String, Node> candidates = new LinkedHashMap<>();
        for (TargetDescriptor descriptor : targetLogic.listTargetDescriptors(null)) {
            if (!descriptor.ref().sourceId().equals(sourceId)) {
                continue;
            }
            if (descriptor.ref().targetType() == TargetType.SKILL
                    && descriptor.ref().externalTargetId().equals(skillId)
                    && descriptor.ref().externalVersionId().equals(version)) {
                Node node = targetNode(descriptor);
                candidates.put(node.id, node);
            }
            for (SkillDescriptor skill : descriptor.skills()) {
                if (skill.externalSkillId().equals(skillId)
                        && skill.externalVersionId().equals(version)) {
                    Node node = skillNode(descriptor, skill);
                    candidates.put(node.id, node);
                }
            }
        }
        Node root = selectNode(candidates.values(), "Skill version",
                sourceId + "/" + skillId + "/" + version, contentHash);
        List<EvaluationRun> runs = runLogic.listRunsBySkillVersion(sourceId, skillId,
                version, limit, root.contentSha256, teamId());
        return expandReverse(root, runs, true).payload();
    }

    /**
     * 评测器血缘图.
     *
     * @param evaluatorId 评测器 id
     * @param version 版本
     * @param contentHash 内容摘要(可空)
     * @param limit 上限
     * @return 图投影
     */
    public Map<String, Object> evaluatorLineage(String evaluatorId, String version,
            String contentHash, int limit) {
        List<EvaluationRun> runs = runLogic.listRunsByEvaluatorVersion(evaluatorId,
                version, limit, teamId());
        Map<String, Node> candidates = new LinkedHashMap<>();
        for (EvaluationRun run : runs) {
            for (EvaluatorSpec evaluator : run.manifest().evaluatorSpecs()) {
                if (evaluator.id().equals(evaluatorId)
                        && evaluator.version().equals(version)) {
                    Node node = evaluatorNode(evaluator);
                    candidates.put(node.id, node);
                }
            }
        }
        Node root = selectNode(candidates.values(), "Evaluator version",
                evaluatorId + "/" + version, contentHash);
        return expandReverse(root, runs, true).payload();
    }

    private static List<String> edge(String source, String target, String relation) {
        List<String> edge = new ArrayList<>(3);
        edge.add(source);
        edge.add(target);
        edge.add(relation);
        return edge;
    }

    private Graph buildRunGraph(EvaluationRun run, TargetDescriptor descriptor) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        Set<List<String>> edges = new HashSet<>();

        Node runNode = runNode(run);
        Node datasetNode = datasetNode(run.manifest().dataset());
        Node targetNode = targetNode(descriptor);
        addNode(nodes, runNode);
        addNode(nodes, datasetNode);
        addNode(nodes, targetNode);
        edges.add(edge(runNode.id, datasetNode.id, "uses_dataset"));
        String targetRelation = descriptor.ref().targetType() == TargetType.AGENT
                ? "evaluates_agent" : "evaluates_skill";
        edges.add(edge(runNode.id, targetNode.id, targetRelation));

        for (Case caseItem : run.manifest().executionCases()) {
            Node caseNode = caseNode(run.manifest().dataset(), caseItem);
            addNode(nodes, caseNode);
            edges.add(edge(datasetNode.id, caseNode.id, "contains_case"));
        }
        if (descriptor.ref().targetType() == TargetType.AGENT) {
            for (SkillDescriptor skill : descriptor.skills()) {
                Node skillNode = skillNode(descriptor, skill);
                addNode(nodes, skillNode);
                edges.add(edge(targetNode.id, skillNode.id, "includes_skill"));
            }
        }
        for (EvaluatorSpec evaluator : run.manifest().evaluatorSpecs()) {
            Node evaluatorNode = evaluatorNode(evaluator);
            addNode(nodes, evaluatorNode);
            edges.add(edge(runNode.id, evaluatorNode.id, "uses_evaluator"));
        }
        return graph(runNode.id, nodes, edges);
    }

    private Graph expandReverse(Node root, List<EvaluationRun> runs) {
        return expandReverse(root, runs, false);
    }

    private Graph expandReverse(Node root, List<EvaluationRun> runs,
            boolean requireRootInRun) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        Set<List<String>> edges = new HashSet<>();
        nodes.put(root.id, root);
        for (EvaluationRun run : runs) {
            TargetDescriptor descriptor = resolveDescriptor(run);
            Graph graph = buildRunGraph(run, descriptor);
            if (requireRootInRun && graph.find(root.id) == null) {
                continue;
            }
            for (Node node : graph.nodes.values()) {
                addNode(nodes, node);
            }
            edges.addAll(graph.edges);
        }
        return graph(root.id, nodes, edges);
    }

    private EvaluationRun requireRun(String runId) {
        EvaluationRun run = runLogic.getRun(runId, teamId());
        if (run == null) {
            throw new AgentException(404, "unknown EvaluationRun: " + runId);
        }
        return run;
    }

    private TargetDescriptor resolveDescriptor(EvaluationRun run) {
        TargetDescriptor descriptor = targetLogic.getTargetDescriptor(
                run.manifest().target().descriptorSha256());
        if (descriptor == null) {
            throw new AgentException(409,
                    "unknown TargetDescriptor: " + run.manifest().target().descriptorSha256());
        }
        return descriptor;
    }

    private static TargetDescriptor selectDescriptor(List<TargetDescriptor> descriptors,
            String contentHash) {
        List<TargetDescriptor> filtered = new ArrayList<>(descriptors);
        if (contentHash != null) {
            DomainValidations.requireSha256(contentHash, "Target content_sha256");
            filtered.clear();
            for (TargetDescriptor item : descriptors) {
                if (item.contentSha256().equals(contentHash)) {
                    filtered.add(item);
                }
            }
        }
        if (filtered.isEmpty()) {
            throw new AgentException(404, "unknown TargetDescriptor version");
        }
        if (filtered.size() > 1) {
            throw new AgentException(409, "Target version has multiple content hashes");
        }
        return filtered.get(0);
    }

    private static Node selectNode(Iterable<Node> candidates, String kind, String identity,
            String contentHash) {
        List<Node> filtered = new ArrayList<>();
        for (Node node : candidates) {
            filtered.add(node);
        }
        if (contentHash != null) {
            DomainValidations.requireSha256(contentHash, kind + " content_sha256");
            List<Node> matched = new ArrayList<>();
            for (Node node : filtered) {
                if (contentHash.equals(node.contentSha256)) {
                    matched.add(node);
                }
            }
            filtered = matched;
        }
        if (filtered.isEmpty()) {
            throw new AgentException(404, "unknown " + kind + ": " + identity);
        }
        if (filtered.size() > 1) {
            throw new AgentException(409, kind + " has multiple content hashes");
        }
        return filtered.get(0);
    }

    private static Node runNode(EvaluationRun run) {
        return new Node("run:" + run.id(), "run", run.id(), "Evaluation Run " + run.id(),
                null, run.manifest().manifestSha256());
    }

    private static Node datasetNode(DatasetVersion dataset) {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("dataset_id", dataset.datasetId());
        identity.put("version", dataset.version());
        identity.put("content_sha256", dataset.contentSha256());
        String label = dataset.datasetName().isEmpty() ? dataset.datasetId()
                : dataset.datasetName();
        return new Node(hashedNodeId("dataset", identity), "dataset", dataset.datasetId(),
                label, String.valueOf(dataset.version()), dataset.contentSha256());
    }

    private static Node caseNode(DatasetVersion dataset, Case caseItem) {
        String caseHash = ContentSha256.of(caseItem.toPayload());
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("dataset_id", dataset.datasetId());
        identity.put("dataset_version", dataset.version());
        identity.put("case_id", caseItem.id());
        identity.put("content_sha256", caseHash);
        return new Node(hashedNodeId("case", identity), "case", caseItem.id(),
                caseItem.name(), null, caseHash);
    }

    private static Node targetNode(TargetDescriptor descriptor) {
        String kind = descriptor.ref().targetType().wireValue();
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("source_id", descriptor.ref().sourceId());
        identity.put("external_id", descriptor.ref().externalTargetId());
        identity.put("version", descriptor.ref().externalVersionId());
        identity.put("content_sha256", descriptor.contentSha256());
        return new Node(hashedNodeId(kind, identity), kind,
                descriptor.ref().externalTargetId(), descriptor.displayName(),
                descriptor.ref().externalVersionId(), descriptor.contentSha256());
    }

    private static Node skillNode(TargetDescriptor descriptor, SkillDescriptor skill) {
        String skillHash = ContentSha256.of(skill.toPayload());
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("source_id", descriptor.ref().sourceId());
        identity.put("external_id", skill.externalSkillId());
        identity.put("version", skill.externalVersionId());
        identity.put("content_sha256", skillHash);
        return new Node(hashedNodeId("skill", identity), "skill",
                skill.externalSkillId(), skill.name(), skill.externalVersionId(), skillHash);
    }

    private static Node evaluatorNode(EvaluatorSpec evaluator) {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("id", evaluator.id());
        identity.put("version", evaluator.version());
        identity.put("content_sha256", evaluator.contentSha256());
        return new Node(hashedNodeId("evaluator", identity), "evaluator", evaluator.id(),
                evaluator.name(), evaluator.version(), evaluator.contentSha256());
    }

    private static String hashedNodeId(String kind, Object identity) {
        return kind + ":" + ContentSha256.of(identity);
    }

    private static void addNode(Map<String, Node> nodes, Node node) {
        Node existing = nodes.get(node.id);
        if (existing != null && !existing.equals(node)) {
            throw new AgentException(409, "lineage node identity collision");
        }
        nodes.put(node.id, node);
    }

    private static Graph graph(String rootId, Map<String, Node> nodes,
            Set<List<String>> edges) {
        List<Node> ordered = new ArrayList<>(nodes.values());
        ordered.sort(java.util.Comparator.comparing((Node node) -> node.kind)
                .thenComparing(node -> node.id));
        List<List<String>> orderedEdges = new ArrayList<>(edges);
        orderedEdges.sort(java.util.Comparator
                .comparing((List<String> edge) -> edge.get(2))
                .thenComparing(edge -> edge.get(0))
                .thenComparing(edge -> edge.get(1)));
        return new Graph(rootId, ordered, orderedEdges);
    }

    private static String teamId() {
        return UserContextHolder.current().userTeamId();
    }

    private static final class Node {
        private final String id;
        private final String kind;
        private final String externalId;
        private final String label;
        private final String version;
        private final String contentSha256;

        private Node(String id, String kind, String externalId, String label,
                String version, String contentSha256) {
            this.id = id;
            this.kind = kind;
            this.externalId = externalId;
            this.label = label;
            this.version = version;
            this.contentSha256 = contentSha256;
        }

        Map<String, Object> payload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", id);
            payload.put("kind", kind);
            payload.put("external_id", externalId);
            payload.put("label", label);
            payload.put("version", version);
            payload.put("content_sha256", contentSha256);
            return payload;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Node)) {
                return false;
            }
            Node node = (Node) other;
            return java.util.Objects.equals(id, node.id)
                    && java.util.Objects.equals(kind, node.kind)
                    && java.util.Objects.equals(externalId, node.externalId)
                    && java.util.Objects.equals(label, node.label)
                    && java.util.Objects.equals(version, node.version)
                    && java.util.Objects.equals(contentSha256, node.contentSha256);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, kind, externalId, label, version, contentSha256);
        }
    }

    private static final class Graph {
        private final String rootId;
        private final Map<String, Node> nodes;
        private final Set<List<String>> edges;
        private final List<Node> orderedNodes;
        private final List<List<String>> orderedEdges;

        private Graph(String rootId, List<Node> orderedNodes, List<List<String>> orderedEdges) {
            this.rootId = rootId;
            this.orderedNodes = orderedNodes;
            this.orderedEdges = orderedEdges;
            this.nodes = new LinkedHashMap<>();
            for (Node node : orderedNodes) {
                this.nodes.put(node.id, node);
            }
            this.edges = new HashSet<>(orderedEdges);
        }

        Node find(String id) {
            return nodes.get(id);
        }

        Map<String, Object> payload() {
            List<Map<String, Object>> nodePayloads = new ArrayList<>(orderedNodes.size());
            for (Node node : orderedNodes) {
                nodePayloads.add(node.payload());
            }
            List<Map<String, Object>> edgePayloads = new ArrayList<>(orderedEdges.size());
            for (List<String> edge : orderedEdges) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("source_id", edge.get(0));
                payload.put("target_id", edge.get(1));
                payload.put("relation", edge.get(2));
                edgePayloads.add(payload);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("root_node_id", rootId);
            payload.put("nodes", nodePayloads);
            payload.put("edges", edgePayloads);
            return payload;
        }
    }
}
