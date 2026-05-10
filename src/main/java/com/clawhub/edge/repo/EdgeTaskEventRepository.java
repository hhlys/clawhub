package com.clawhub.edge.repo;

import com.clawhub.edge.domain.EdgeTaskEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EdgeTaskEventRepository extends JpaRepository<EdgeTaskEvent, Long> {

    List<EdgeTaskEvent> findByTaskIdAndIdGreaterThanOrderByIdAsc(String taskId, Long id);

    List<EdgeTaskEvent> findTop200ByConversationIdOrderByIdAsc(String conversationId);
}
