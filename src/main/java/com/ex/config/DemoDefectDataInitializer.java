package com.ex.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.DefectRecord;
import com.ex.entity.DefectRecord.DefectType;
import com.ex.entity.DefectRecord.OccurrenceStage;
import com.ex.entity.DefectRecord.ResolutionType;
import com.ex.entity.ProductLot;
import com.ex.repository.DefectRecordRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.service.DefectService;
import com.ex.service.SellableStockQuery;

import lombok.RequiredArgsConstructor;

/**
 * 발표 화면에서 불량 관리의 통계, 검색, 상태별 처리를 시연할 수 있도록
 * 최초 한 번만 예시 불량 이력을 생성한다.
 */
@Component
@Order(500)
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "feedflow.demo.defects-enabled",
        havingValue = "true")
public class DemoDefectDataInitializer implements ApplicationRunner {

    private record DefectSeed(
            int quantity,
            DefectType type,
            OccurrenceStage stage,
            String description,
            String reporter,
            int occurredDaysAgo) {
    }

    private static final List<DefectSeed> SEEDS = List.of(
            new DefectSeed(2, DefectType.DAMAGE, OccurrenceStage.RECEIVING,
                    "입고 검수 중 포대 하단 찢김과 사료 누출 확인", "김민준", 1),
            new DefectSeed(3, DefectType.CONTAMINATION, OccurrenceStage.STORAGE,
                    "보관 구역 점검 중 포대 외부 수분 흔적 발견", "이서연", 2),
            new DefectSeed(1, DefectType.SPECIFICATION, OccurrenceStage.PRODUCTION,
                    "표기 중량 대비 실측 중량 부족으로 규격 재검사 필요", "박지훈", 3),
            new DefectSeed(2, DefectType.FUNCTION, OccurrenceStage.SHIPPING,
                    "출고 검사에서 포장 밀봉 불량 확인", "최유진", 4),
            new DefectSeed(1, DefectType.EXPIRED, OccurrenceStage.STORAGE,
                    "선입선출 점검 중 판매 가능 기한 기준 미달 확인", "정도윤", 5),
            new DefectSeed(2, DefectType.DAMAGE, OccurrenceStage.SHIPPING,
                    "상차 전 지게차 접촉으로 포대 측면 손상", "한수빈", 6),
            new DefectSeed(1, DefectType.OTHER, OccurrenceStage.RECEIVING,
                    "LOT 라벨 일부 번짐으로 식별 정보 재확인 필요", "오현우", 7),
            new DefectSeed(2, DefectType.CONTAMINATION, OccurrenceStage.RETURNED,
                    "배송 중 빗물 노출 의심으로 고객 회수 후 격리", "강하은", 8),
            new DefectSeed(1, DefectType.SPECIFICATION, OccurrenceStage.PRODUCTION,
                    "영양성분 검사 결과 내부 관리 기준 편차 확인", "윤시우", 9),
            new DefectSeed(2, DefectType.DAMAGE, OccurrenceStage.RETURNED,
                    "농장 하차 과정에서 파렛트 모서리에 눌린 상품 회수", "임지아", 10));

    private final DefectRecordRepository defectRepository;
    private final ProductLotRepository lotRepository;
    private final DefectService defectService;
    private final SellableStockQuery sellableStockQuery;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (defectRepository.count() > 0) {
            return;
        }

        List<ProductLot> lots = lotRepository.findAllByOrderByExpirationDateAsc();
        if (lots.isEmpty()) {
            return;
        }
        Map<Long, Integer> availableByLot = sellableStockQuery.sellablePerLot(
                lots.stream().map(ProductLot::getLotId).toList());
        List<Long> createdIds = new ArrayList<>();

        for (int index = 0; index < SEEDS.size(); index++) {
            DefectSeed seed = SEEDS.get(index);
            ProductLot lot = findAvailableLot(lots, availableByLot,
                    seed.quantity(), index);
            boolean registerAsReturned = seed.stage() == OccurrenceStage.RETURNED
                    || lot == null;
            if (lot == null) lot = lots.get(index % lots.size());

            if (registerAsReturned) {
                defectService.registerReturned(
                        lot.getLotId(), seed.quantity(), seed.type(),
                        seed.description(), seed.reporter());
            } else {
                defectService.register(
                        lot.getLotId(), seed.quantity(), seed.type(), seed.stage(),
                        seed.description(), seed.reporter(),
                        LocalDateTime.now().minusDays(seed.occurredDaysAgo()));
                availableByLot.computeIfPresent(lot.getLotId(),
                        (ignored, available) -> available - seed.quantity());
            }

            defectRepository.findAll().stream()
                    .max(Comparator.comparing(DefectRecord::getDefectId))
                    .map(DefectRecord::getDefectId)
                    .ifPresent(createdIds::add);
        }

        // 4건은 격리 상태로 두고, 3건은 검사 중, 3건은 처리 완료로 구성한다.
        for (int index = 4; index < Math.min(7, createdIds.size()); index++) {
            defectService.startInspection(createdIds.get(index));
        }
        for (int index = 7; index < createdIds.size(); index++) {
            Long defectId = createdIds.get(index);
            defectService.startInspection(defectId);
            defectService.resolve(
                    defectId,
                    index % 2 == 0
                            ? ResolutionType.SUPPLIER_RETURN
                            : ResolutionType.DISPOSAL,
                    "품질관리팀",
                    index % 2 == 0
                            ? "검사 결과 제조사 귀책으로 확인되어 반품 처리"
                            : "교차오염 방지를 위해 승인 절차 후 폐기 처리");
        }
    }

    private ProductLot findAvailableLot(
            List<ProductLot> lots,
            Map<Long, Integer> availableByLot,
            int quantity,
            int startIndex) {
        if (lots.isEmpty()) {
            return null;
        }
        for (int offset = 0; offset < lots.size(); offset++) {
            ProductLot lot = lots.get((startIndex + offset) % lots.size());
            if (availableByLot.getOrDefault(lot.getLotId(), 0) >= quantity) {
                return lot;
            }
        }
        return null;
    }
}
