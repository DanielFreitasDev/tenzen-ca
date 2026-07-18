package dev.tenzen.ca.issuance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssuanceRepository extends JpaRepository<IssuedCertificate, Long> {

    List<IssuedCertificate> findAllByOrderByIssuedAtDesc();

    List<IssuedCertificate> findByStatusOrderByIssuedAtDesc(IssuedCertificate.Status status);

    /**
     * Busca por nome (CN), documento ou serial. A cláusula de documento só participa
     * quando o termo tem dígitos: com {@code digits} vazio, um LIKE '%%' casaria com
     * todas as linhas e anularia os outros filtros do OR.
     */
    @Query("""
            select c from IssuedCertificate c
            where lower(c.subjectCn) like lower(concat('%', :term, '%'))
               or (:digits <> '' and c.document like concat('%', :digits, '%'))
               or lower(c.serialHex) like lower(concat('%', :term, '%'))
            order by c.issuedAt desc
            """)
    List<IssuedCertificate> search(@Param("term") String term, @Param("digits") String digits);

    boolean existsBySerialHex(String serialHex);
}
