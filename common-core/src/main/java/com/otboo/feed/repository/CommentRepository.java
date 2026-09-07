package com.otboo.feed.repository;

import com.otboo.feed.entity.Comment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** 삭제 권한 검사에 댓글 작성자와 피드 작성자가 모두 필요하다. */
    @Query("select c from Comment c join fetch c.author join fetch c.feed f join fetch f.author "
            + "where c.id = :id")
    Optional<Comment> findWithAuthorById(@Param("id") UUID id);
}
