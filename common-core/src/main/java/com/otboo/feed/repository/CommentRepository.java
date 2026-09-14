package com.otboo.feed.repository;

import com.otboo.feed.entity.Comment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** 삭제 권한 검사에 댓글 작성자와 피드 작성자가 모두 필요하다. */
    @Query("select c from Comment c join fetch c.author join fetch c.feed f join fetch f.author "
            + "where c.id = :id")
    Optional<Comment> findWithAuthorById(@Param("id") UUID id);

    /**
     * 댓글 삭제.
     * <p>{@code feed_id} 조건을 함께 건다.
     * 지우는 문장 자체가 다른 피드의 댓글을 건드릴 수 없게 한다.
     * @return 실제로 지워진 행 수. 0 이면 그 사이 이미 사라짐.
     */
    @Modifying
    @Query("delete from Comment c where c.id = :id and c.feed.id = :feedId")
    long deleteByIdAndFeedId(@Param("id") UUID id, @Param("feedId") UUID feedId);
}
