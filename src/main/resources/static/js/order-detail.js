(() => {
    "use strict";

    const page = document.querySelector("#order-detail-page");
    const toast = document.querySelector("#toast");
    const orderNumber = page?.dataset.orderNumber;

    const statusLabels = {
        PAYMENT_PENDING: "결제 대기",
        PAID: "결제 완료",
        PREPARING: "상품 준비중",
        SHIPPING: "배송중",
        DELIVERED: "배송 완료",
        CANCELLED: "주문 취소"
    };

    const paymentLabels = {
        CARD: "신용카드",
        KAKAO_PAY: "카카오페이",
        BANK_TRANSFER: "무통장입금(가상계좌)"
    };

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#039;");
    }

    function won(value) {
        return `${Number(value || 0).toLocaleString("ko-KR")}원`;
    }

    function dateTime(value) {
        return value ? new Date(value).toLocaleString("ko-KR") : "-";
    }

    function showToast(message, error = false) {
        toast.textContent = message;
        toast.classList.toggle("error", error);
        toast.classList.add("show");
    }

    async function api(url) {
        const response = await fetch(url, { headers: { Accept: "application/json" } });
        if (!response.ok) {
            let message = `요청 실패 (${response.status})`;
            try {
                const body = await response.json();
                message = body.message || message;
            } catch {
                // JSON 오류 응답이 아니면 기본 메시지를 사용합니다.
            }
            throw new Error(message);
        }
        return response.json();
    }

    function renderTimeline(order) {
        const stages = [
            ["PAYMENT_PENDING", "주문 접수", order.orderedAt],
            ["PAID", "결제 완료", order.paymentApprovedAt],
            ["PREPARING", "상품 준비", order.preparingAt],
            ["SHIPPING", "배송 중", order.shippedAt],
            ["DELIVERED", "배송 완료", order.deliveredAt]
        ];
        const ranks = Object.fromEntries(stages.map(([status], index) => [status, index]));
        const currentRank = order.status === "CANCELLED"
            ? (order.paymentStatus === "DONE" ? 1 : 0)
            : (ranks[order.status] ?? 0);

        document.querySelector("#delivery-timeline").innerHTML = stages
            .map(([status, label, date], index) => {
                const stateClass = index < currentRank
                    ? "complete"
                    : index === currentRank && order.status !== "CANCELLED"
                    ? "current"
                    : "";
                return `<article class="${stateClass}">
                    <span>${index + 1}</span>
                    <strong>${label}</strong>
                    <small>${dateTime(date)}</small>
                </article>`;
            })
            .join("");

        if (order.status === "CANCELLED") {
            document.querySelector("#delivery-timeline").insertAdjacentHTML(
                "beforeend",
                `<article class="cancelled current"><span>×</span><strong>주문 취소</strong><small>${dateTime(order.cancelledAt)}</small></article>`
            );
        }

        const tracking = document.querySelector("#tracking-summary");
        if (order.trackingNumber) {
            tracking.hidden = false;
            tracking.innerHTML = `<span>배송 조회 정보</span><strong>${escapeHtml(order.trackingCarrier)} · ${escapeHtml(order.trackingNumber)}</strong>`;
        }
    }

    function renderItems(order) {
        const rows = (order.items || []).map((item) => {
            const lots = (item.lots || []).length
                ? item.lots.map((lot) => `
                    <span class="detail-lot">
                        <strong>${escapeHtml(lot.lotNumber)}</strong>
                        <small>${escapeHtml(lot.expirationDate)}까지 · ${lot.quantity}포</small>
                    </span>`).join("")
                : "<span class=\"detail-lot\">배정 LOT 없음</span>";
            return `<tr>
                <td><strong>${escapeHtml(item.productName)}</strong><small>총 ${Number(item.totalWeightKg).toLocaleString("ko-KR")}kg</small></td>
                <td>${item.quantity}포</td>
                <td>${lots}</td>
                <td><strong>${won(item.lineAmount)}</strong><small>단가 ${won(item.unitPrice)}</small></td>
            </tr>`;
        }).join("");
        document.querySelector("#detail-items").innerHTML = rows || "<tr><td colspan=\"4\">주문 상품이 없습니다.</td></tr>";

        document.querySelector("#detail-totals").innerHTML = `
            <div><dt>상품 금액</dt><dd>${won(order.productAmount)}</dd></div>
            <div><dt>배송비</dt><dd>${won(order.deliveryFee)}</dd></div>
            <div><dt>할인</dt><dd>-${won(order.discountAmount)}</dd></div>
            <div class="total"><dt>총 결제금액</dt><dd>${won(order.totalAmount)}</dd></div>`;
    }

    function renderInformation(order) {
        const fullAddress = [order.postalCode && `(${order.postalCode})`, order.address, order.detailAddress]
            .filter(Boolean)
            .join(" ");
        document.querySelector("#detail-delivery").innerHTML = `
            <div><dt>받는 분</dt><dd>${escapeHtml(order.customerName)}</dd></div>
            <div><dt>연락처</dt><dd>${escapeHtml(order.phone)}</dd></div>
            <div><dt>배송지</dt><dd>${escapeHtml(fullAddress)}</dd></div>
            <div><dt>하차 위치</dt><dd>${escapeHtml(order.unloadingLocation || "-")}</dd></div>
            <div><dt>배송 요청</dt><dd>${escapeHtml(order.deliveryRequest || "-")}</dd></div>`;

        const virtualAccount = order.virtualAccountNumber
            ? `<div><dt>입금 계좌</dt><dd>${escapeHtml(order.virtualAccountBank)} ${escapeHtml(order.virtualAccountNumber)}</dd></div>
               <div><dt>입금 기한</dt><dd>${escapeHtml(order.virtualAccountDueDate || "-")}</dd></div>`
            : "";
        document.querySelector("#detail-payment").innerHTML = `
            <div><dt>결제수단</dt><dd>${escapeHtml(paymentLabels[order.paymentMethod] || order.paymentMethod)}</dd></div>
            <div><dt>결제상태</dt><dd>${escapeHtml(statusLabels[order.status] || order.status)}</dd></div>
            <div><dt>결제일시</dt><dd>${dateTime(order.paymentApprovedAt)}</dd></div>
            ${virtualAccount}`;

        const receipt = document.querySelector("#detail-receipt");
        const receiptNote = document.querySelector("#detail-receipt-note");
        receipt.hidden = true;
        receipt.removeAttribute("href");
        receiptNote.hidden = true;
        receiptNote.textContent = "";

        if (order.receiptUrl) {
            try {
                const receiptUrl = new URL(order.receiptUrl, window.location.origin);
                const receiptHost = receiptUrl.hostname.toLowerCase();
                const isKakaoTestReceipt = receiptHost === "mockup-pg-web.kakao.com"
                    || receiptHost.includes("mockup-pg-web");

                if (isKakaoTestReceipt) {
                    receipt.href = `/mypage/orders/${encodeURIComponent(order.orderNumber)}/receipt`;
                    receipt.textContent = "테스트 결제 확인서 보기 →";
                    receipt.hidden = false;
                    receiptNote.textContent = "테스트용 결제 확인서이며 카드전표·현금영수증 등 세무 증빙으로 사용할 수 없습니다.";
                    receiptNote.hidden = false;
                } else if (["http:", "https:"].includes(receiptUrl.protocol)) {
                    receipt.href = receiptUrl.href;
                    receipt.textContent = "결제 영수증 보기 →";
                    receipt.hidden = false;
                }
            } catch {
                // 유효하지 않은 영수증 URL은 표시하지 않습니다.
            }
        }
    }

    async function loadOrder() {
        if (!orderNumber) {
            showToast("주문번호가 없습니다.", true);
            return;
        }
        try {
            const order = await api(`/api/orders/${encodeURIComponent(orderNumber)}`);
            document.querySelector("#detail-order-number").textContent = order.orderNumber;
            const status = document.querySelector("#detail-status");
            status.textContent = statusLabels[order.status] || order.status;
            status.className = `detail-status status-${String(order.status).toLowerCase()}`;
            renderTimeline(order);
            renderItems(order);
            renderInformation(order);
        } catch (error) {
            showToast(error.message, true);
            document.querySelector("#detail-items").innerHTML = `<tr><td colspan="4">${escapeHtml(error.message)}</td></tr>`;
        }
    }

    loadOrder();
})();
