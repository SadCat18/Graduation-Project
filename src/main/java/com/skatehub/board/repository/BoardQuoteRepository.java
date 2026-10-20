package com.skatehub.board.repository;
import com.skatehub.board.entity.BoardQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
public interface BoardQuoteRepository extends JpaRepository<BoardQuote, Long> {
    java.util.Optional<BoardQuote> findFirstByKeyOrderByVersionDesc(String key);
    java.util.List<BoardQuote> findBySourceProductKeyIn(java.util.Collection<String> keys);
    @Query("select q from BoardQuote q where not exists (select newer.id from BoardQuote newer where newer.key=q.key and newer.version>q.version)")
    java.util.List<BoardQuote> findLatest();
    @Query("select q from BoardQuote q where q.sourceProductKey in :keys and not exists (select newer.id from BoardQuote newer where newer.key=q.key and newer.version>q.version)")
    java.util.List<BoardQuote> findLatestBySourceProductKeyIn(@Param("keys") java.util.Collection<String> keys);
    @Query(value="select q from BoardQuote q where not exists (select newer.id from BoardQuote newer where newer.key=q.key and newer.version>q.version)",
           countQuery="select count(q) from BoardQuote q where not exists (select newer.id from BoardQuote newer where newer.key=q.key and newer.version>q.version)")
    Page<BoardQuote> findLatestPage(Pageable pageable);
}
