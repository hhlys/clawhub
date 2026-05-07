package com.clawhub.edge.repo;

import com.clawhub.edge.domain.EdgeNode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EdgeNodeRepository extends JpaRepository<EdgeNode, Long> {

    Optional<EdgeNode> findByNodeId(String nodeId);
}
