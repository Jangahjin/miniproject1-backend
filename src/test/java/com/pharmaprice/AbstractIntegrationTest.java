package com.pharmaprice;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 pharmaprice_test DB(Docker 미사용, docs/ROADMAP.md T-06 참고)를 사용하는 통합 테스트 베이스.
 * 각 테스트는 @Transactional 롤백으로 격리한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {
}
