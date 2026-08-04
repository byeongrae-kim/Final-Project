(() => {
    "use strict";

    const page = document.querySelector("#receipt-page");
    const toast = document.querySelector("#toast");
    const orderNumber = page?.dataset.orderNumber;

    const paymentLabels = {
        CARD: "일반 신용카드",
        KAKAO_PAY: "카카오페이",
        BANK_TRANSFER: "무통장입금(가상계좌)"
    };

    const paymentStatusLabels = {
        READY: "결제 대기",
        WAITING_FOR_DEPOSIT: "입금 대기",
        DONE: "결제 완료",
        CANCELLED: "결제 취소",
        FAILED: "결제 실패"
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

    function showError(message) {
        toast.textContent = message;
        toast.classList.add("error", "show");
    }

    async function loadReceipt() {
        if (!orderNumber) {
            showError("주문번호가 없습니다.");
            return;
        }

        try {
            const response = await fetch(`/api/orders/${encodeURIComponent(orderNumber)}`, {
                headers: { Accept: "application/json" }
            });
            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                throw new Error(body.message || `결제 정보 조회 실패 (${response.status})`);
            }

            const order = await response.json();
            document.querySelector("#receipt-grand-heading").textContent = won(order.totalAmount);
            document.querySelector("#receipt-meta").innerHTML = `
                <div><dt>주문번호</dt><dd>${escapeHtml(order.orderNumber)}</dd></div>
                <div><dt>주문자</dt><dd>${escapeHtml(order.customerName)}</dd></div>
                <div><dt>결제수단</dt><dd>${escapeHtml(paymentLabels[order.paymentMethod] || order.paymentMethod)}</dd></div>
                <div><dt>결제상태</dt><dd>${escapeHtml(paymentStatusLabels[order.paymentStatus] || order.paymentStatus)}</dd></div>
                <div><dt>주문일시</dt><dd>${dateTime(order.orderedAt)}</dd></div>
                <div><dt>승인일시</dt><dd>${dateTime(order.paymentApprovedAt)}</dd></div>`;

            document.querySelector("#receipt-items").innerHTML = (order.items || []).map((item) => `
                <tr>
                    <td>${escapeHtml(item.productName)}</td>
                    <td>${Number(item.quantity).toLocaleString("ko-KR")}</td>
                    <td>${won(item.unitPrice)}</td>
                    <td>${won(item.lineAmount)}</td>
                </tr>`).join("") || "<tr><td colspan=\"4\">주문 상품이 없습니다.</td></tr>";

            document.querySelector("#receipt-totals").innerHTML = `
                <div><dt>상품 구매금액</dt><dd>${won(order.productAmount)}</dd></div>
                <div><dt>배송비</dt><dd>${won(order.deliveryFee)}</dd></div>
                <div><dt>할인금액</dt><dd>-${won(order.discountAmount)}</dd></div>
                <div class="grand"><dt>총 결제금액</dt><dd>${won(order.totalAmount)}</dd></div>`;
        } catch (error) {
            showError(error.message);
            document.querySelector("#receipt-items").innerHTML = `<tr><td colspan="4">${escapeHtml(error.message)}</td></tr>`;
        }
    }

    document.querySelector("#receipt-print")?.addEventListener("click", () => window.print());
    loadReceipt();
})();
