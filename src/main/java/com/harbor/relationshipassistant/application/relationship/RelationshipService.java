package com.harbor.relationshipassistant.application.relationship;

import com.harbor.relationshipassistant.common.exception.ValidationException;
import com.harbor.relationshipassistant.domain.relationship.Relationship;
import com.harbor.relationshipassistant.domain.relationship.RelationshipStage;
import com.harbor.relationshipassistant.domain.relationship.RelationshipStatus;
import com.harbor.relationshipassistant.infrastructure.persistence.AuditLogRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.DataSourceFactory;
import com.harbor.relationshipassistant.infrastructure.persistence.RelationshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Relationship 业务（技术设计 §11/§12/§39）。阶段变更必须同事务写历史。 */
public class RelationshipService {

    private static final Logger log = LoggerFactory.getLogger(RelationshipService.class);

    private final DataSourceFactory ds;
    private final RelationshipRepository repository;
    private final AuditLogRepository audit;

    public RelationshipService(DataSourceFactory ds, RelationshipRepository repository, AuditLogRepository audit) {
        this.ds = ds;
        this.repository = repository;
        this.audit = audit;
    }

    public Relationship create(String name, String myName, RelationshipStage stage) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("对方昵称不能为空", "RelationshipService.create");
        }
        Relationship r = repository.insert(Relationship.createNew(name.trim(), myName, stage));
        audit.log(r.getId(), "RELATIONSHIP_CREATE", "name=" + name);
        log.info("[REL] 创建关系 id={} name={}", r.getId(), r.getName());
        return r;
    }

    public Relationship get(Long id) { return repository.findById(id); }

    public List<Relationship> listActive() { return repository.listActive(); }

    /** 用户手动设置阶段；AI 不得自动调用本方法（PRD §4.2）。 */
    public void changeStage(Long relationshipId, RelationshipStage newStage) {
        if (newStage == null) throw new ValidationException("阶段不能为空", "changeStage");
        Connection c = null;
        try {
            c = ds.newConnection();
            c.setAutoCommit(false);
            repository.updateStage(c, relationshipId, newStage);
            repository.writeStageHistory(c, relationshipId, newStage);
            c.commit();
        } catch (SQLException e) {
            try { if (c != null) c.rollback(); } catch (SQLException ignored) {}
            throw new ValidationException("更新阶段失败", "changeStage");
        } finally {
            if (c != null) try { c.close(); } catch (SQLException ignored) {}
        }
        audit.log(relationshipId, "STAGE_CHANGE", "stage=" + newStage);
    }

    public void archive(Long id) {
        repository.updateStatus(id, RelationshipStatus.ARCHIVED);
        audit.log(id, "RELATIONSHIP_ARCHIVE", null);
    }

    /** 删除：V1 软删除（status=DELETED），关联数据由外键级联；真删由用户二次确认后另行处理。 */
    public void delete(Long id) {
        repository.updateStatus(id, RelationshipStatus.DELETED);
        audit.log(id, "RELATIONSHIP_DELETE", "soft-delete");
    }
}
