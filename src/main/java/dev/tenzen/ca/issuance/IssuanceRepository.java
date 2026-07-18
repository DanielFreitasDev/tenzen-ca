package dev.tenzen.ca.issuance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuanceRepository extends JpaRepository<IssuedCertificate, Long> {

    List<IssuedCertificate> findAllByOrderByIssuedAtDesc();

    List<IssuedCertificate> findByStatusOrderByIssuedAtDesc(IssuedCertificate.Status status);

    List<IssuedCertificate>
            findBySubjectCnContainingIgnoreCaseOrDocumentContainingOrSerialHexContainingIgnoreCaseOrderByIssuedAtDesc(
                    String cn, String document, String serial);

    boolean existsBySerialHex(String serialHex);
}
