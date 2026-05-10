package com.clawhub.edge.repo;

import com.clawhub.edge.domain.EdgeTask;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EdgeTaskRepository extends JpaRepository<EdgeTask, Long> {

    Optional<EdgeTask> findByTaskId(String taskId);

    List<EdgeTask> findTop20ByNodeIdOrderByCreatedAtDesc(String nodeId);

    Optional<EdgeTask> findFirstByNodeIdAndStatusOrderByCreatedAtAsc(String nodeId, String status);
}
