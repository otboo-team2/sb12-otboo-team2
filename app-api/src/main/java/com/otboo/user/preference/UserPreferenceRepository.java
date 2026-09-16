package com.otboo.user.preference;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {

    @EntityGraph(attributePaths = {"selectableValue", "selectableValue.definition"})
    List<UserPreference> findAllByUserIdOrderByCreatedAtAscIdAsc(UUID userId);

    void deleteAllByUserId(UUID userId);
}
