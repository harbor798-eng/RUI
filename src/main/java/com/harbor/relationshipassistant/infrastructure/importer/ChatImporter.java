package com.harbor.relationshipassistant.infrastructure.importer;

/**
 * 导入器接口（技术设计 §6.1）。
 * 新增数据源（抖音/小红书/微信 SQLite）只需实现本接口，不改 Chat 业务。
 */
public interface ChatImporter {

    String type();

    ImportPreview parse(ImportRequest request);
}
