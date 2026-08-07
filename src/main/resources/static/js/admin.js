(() => {
    "use strict";

    const form = document.querySelector("#admin-product-form");
    const table = document.querySelector("#admin-product-table");
    const productCount = document.querySelector("#admin-product-count");
    const message = document.querySelector("#admin-message");
    const cancelEditButton = document.querySelector("#cancel-edit");
    const editorTitle = document.querySelector("#editor-title");
    const editorEyebrow = document.querySelector("#editor-eyebrow");
    const saveButton = document.querySelector("#save-product");
    const toast = document.querySelector("#toast");

    const fieldIds = [
        "manufacturerName", "productName", "animalType", "feedStage", "description",
        "weightKg", "price", "originalPrice", "badge", "proteinPercent", "fatPercent",
        "fiberPercent", "calciumPercent", "imageUrl", "displayTone", "displayShape",
        "lotNumber", "manufacturedDate", "expirationDate", "lotQuantity"
    ];

    const state = {
        products: [],
        orders: [],
        alerts: [],
        dashboard: null,
        activities: [],
        editingId: null,
        query: "",
        animal: "ALL",
        stock: "ALL",
        event: "ALL",
        orderQuery: "",
        orderStatus: "ALL"
    };

    const fields = Object.fromEntries(
        fieldIds.map((id) => [id, document.getElementById(id)])
    );

    const animalLabels = {
        CATTLE: "한우",
        DAIRY_CATTLE: "젖소",
        PIG: "돼지",
        CHICKEN: "닭",
        DUCK: "오리",
        PET: "반려동물",
        SUPPLEMENT: "영양제"
    };

    const orderStatusLabels = {
        PAYMENT_PENDING: "결제 대기",
        PAID: "결제 완료",
        PREPARING: "상품 준비중",
        SHIPPING: "배송중",
        DELIVERED: "배송 완료",
        CANCELLED: "취소"
    };

    const nextOrderStatus = {
        PAID: ["PREPARING", "상품 준비 시작"],
        PREPARING: ["SHIPPING", "배송 시작"],
        SHIPPING: ["DELIVERED", "배송 완료 처리"]
    };

    const activityLabels = {
        PRODUCT_CREATED: "상품 등록",
        PRODUCT_UPDATED: "상품 수정",
        PRODUCT_DEACTIVATED: "판매 중지",
        ORDER_STATUS_CHANGED: "주문 상태 변경"
    };

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#039;");
    }

    function number(value) {
        return Number(value ?? 0).toLocaleString("ko-KR");
    }

    function isoDate(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");
        return `${year}-${month}-${day}`;
    }

    function showToast(text, isError = false) {
        toast.textContent = text;
        toast.classList.toggle("error", isError);
        toast.classList.add("show");
        window.clearTimeout(showToast.timer);
        showToast.timer = window.setTimeout(() => toast.classList.remove("show"), 2600);
    }

    function basicAuthorization() {
        const username = document.querySelector("#admin-username").value.trim();
        const password = document.querySelector("#admin-password").value;

        if (!username || !password) {
            throw new Error("관리자 아이디와 비밀번호를 먼저 입력하세요.");
        }

        const bytes = new TextEncoder().encode(`${username}:${password}`);
        let binary = "";
        bytes.forEach((byte) => {
            binary += String.fromCharCode(byte);
        });
        return `Basic ${window.btoa(binary)}`;
    }

    async function readError(response) {
        try {
            const body = await response.json();
            return body.message || body.error || `요청 실패 (${response.status})`;
        } catch {
            return `요청 실패 (${response.status})`;
        }
    }

    async function adminFetch(url, options = {}) {
        const response = await fetch(url, {
            ...options,
            headers: {
                ...(options.headers || {}),
                "Authorization": basicAuthorization()
            }
        });
        if (!response.ok) {
            throw new Error(await readError(response));
        }
        if (response.status === 204) {
            return null;
        }
        return response.json();
    }

    async function loadDashboard() {
        try {
            state.dashboard = await adminFetch("/api/admin/dashboard");
            renderDashboard();
        } catch (error) {
            document.querySelector("#admin-sales-chart").innerHTML =
                `<p>${escapeHtml(error.message)}</p>`;
            showToast(error.message, true);
            throw error;
        }
    }

    function renderDashboard() {
        const dashboard = state.dashboard;
        if (!dashboard) {
            return;
        }

        document.querySelector("#metric-total-revenue").textContent = number(dashboard.totalRevenue);
        document.querySelector("#metric-today-revenue").textContent = number(dashboard.todayRevenue);
        document.querySelector("#metric-total-orders").textContent = number(dashboard.totalOrders);
        document.querySelector("#metric-today-orders").textContent = number(dashboard.todayOrders);
        document.querySelector("#metric-paid-orders").textContent = number(dashboard.paymentCompletedOrders);
        document.querySelector("#metric-shipping-orders").textContent = number(dashboard.shippingOrders);
        document.querySelector("#metric-low").textContent = number(dashboard.lowStockLots);
        document.querySelector("#metric-expiry").textContent = number(dashboard.expiringLots);
        document.querySelector("#summary-products").textContent = `${number(dashboard.totalProducts)}개`;
        document.querySelector("#summary-soldout").textContent = `${number(dashboard.soldOutProducts)}개`;
        document.querySelector("#summary-cancelled").textContent = `${number(dashboard.cancelledOrders)}건`;
        document.querySelector("#summary-alerts").textContent =
            `${number(dashboard.lowStockLots + dashboard.expiringLots)}건`;

        const points = dashboard.dailySales || [];
        const maximum = Math.max(1, ...points.map((point) => Number(point.revenue || 0)));
        document.querySelector("#admin-sales-chart").innerHTML = points.map((point) => {
            const date = new Date(`${point.date}T00:00:00`);
            const height = Math.max(8, Math.round((Number(point.revenue || 0) / maximum) * 150));
            return `<article title="${number(point.revenue)}원 · ${number(point.orderCount)}건">
                <strong>${number(point.revenue)}원</strong>
                <div class="sales-bar-track"><span style="height:${height}px"></span></div>
                <small>${date.toLocaleDateString("ko-KR", { month: "numeric", day: "numeric" })}</small>
                <em>${number(point.orderCount)}건</em>
            </article>`;
        }).join("");
    }

    async function loadActivities() {
        const activityTable = document.querySelector("#admin-activity-table");
        activityTable.innerHTML = "<tr><td colspan=\"6\">활동 이력을 불러오는 중입니다.</td></tr>";
        try {
            state.activities = await adminFetch("/api/admin/activities");
            renderActivities();
        } catch (error) {
            activityTable.innerHTML = `<tr><td colspan="6">${escapeHtml(error.message)}</td></tr>`;
            showToast(error.message, true);
            throw error;
        }
    }

    function renderActivities() {
        const activityTable = document.querySelector("#admin-activity-table");
        document.querySelector("#admin-activity-count").textContent = number(state.activities.length);
        if (!state.activities.length) {
            activityTable.innerHTML = "<tr><td colspan=\"6\">아직 기록된 관리자 변경 작업이 없습니다.</td></tr>";
            return;
        }

        activityTable.innerHTML = state.activities.map((activity) => `<tr>
            <td><strong>${new Date(activity.createdAt).toLocaleString("ko-KR")}</strong></td>
            <td>${escapeHtml(activity.adminUsername)}</td>
            <td><span class="admin-activity-badge">${escapeHtml(activityLabels[activity.actionType] || activity.actionType)}</span></td>
            <td><strong>${escapeHtml(activity.targetType)}</strong><small>${escapeHtml(activity.targetIdentifier)}</small></td>
            <td>${escapeHtml(activity.description)}</td>
            <td>${escapeHtml(activity.ipAddress || "-")}</td>
        </tr>`).join("");
    }

    async function loadProducts() {
        table.innerHTML = "<tr><td colspan=\"7\">상품을 불러오는 중입니다.</td></tr>";

        try {
            const response = await fetch("/api/products");
            if (!response.ok) {
                throw new Error(await readError(response));
            }
            state.products = await response.json();
            renderProducts();
        } catch (error) {
            table.innerHTML = `<tr><td colspan="7">${escapeHtml(error.message)}</td></tr>`;
            showToast(error.message, true);
        }
    }

    function renderProducts() {
        productCount.textContent = state.products.length;

        const query = state.query.toLowerCase();
        const products = state.products.filter((product) => {
            const queryMatch = !query || `${product.name} ${product.productCode} ${product.lot || ""}`.toLowerCase().includes(query);
            const animalMatch = state.animal === "ALL"
                || (state.animal === "CATTLE_GROUP" && ["CATTLE", "DAIRY_CATTLE"].includes(product.animalType))
                || (state.animal === "POULTRY_GROUP" && ["CHICKEN", "DUCK"].includes(product.animalType))
                || state.animal === product.animalType;
            const stockMatch = state.stock === "ALL"
                || (state.stock === "AVAILABLE" && product.stock > 10)
                || (state.stock === "LOW" && product.stock > 0 && product.stock <= 10)
                || (state.stock === "SOLDOUT" && product.stock < 1);
            const eventMatch = state.event === "ALL"
                || (state.event === "EVENT" && Boolean(product.badge || product.originalPrice))
                || (state.event === "NORMAL" && !product.badge && !product.originalPrice);
            return queryMatch && animalMatch && stockMatch && eventMatch;
        });

        if (!products.length) {
            table.innerHTML = "<tr><td colspan=\"7\">조건에 맞는 상품이 없습니다.</td></tr>";
            return;
        }

        table.innerHTML = products.map((product) => {
            const stockLabel = product.stock < 1 ? "품절" : product.stock <= 10 ? "재고 부족" : "판매 중";
            const stockClass = product.stock < 1 ? "sold-out" : product.stock <= 10 ? "low-stock" : "available";
            return `
            <tr>
                <td>
                    <small>${escapeHtml(product.productCode || `FF-P${product.id}`)}</small>
                    <strong>${escapeHtml(product.name)}</strong>
                    <small>${escapeHtml(product.manufacturer)} · ${escapeHtml(product.stage)}</small>
                </td>
                <td>${escapeHtml(product.animal || animalLabels[product.animalType])}</td>
                <td><strong>${number(product.weight)}kg / 포</strong><small>${number(product.price)}원</small></td>
                <td><strong>${escapeHtml(product.lot || "재고 LOT 없음")}</strong><small>${escapeHtml(product.expiry || "-")}</small></td>
                <td><span class="admin-status ${stockClass}">${stockLabel}</span><small>${number(product.stock)}포</small></td>
                <td>${product.badge || product.originalPrice ? `<span class="admin-event">${escapeHtml(product.badge || "할인")}</span>` : "-"}</td>
                <td>
                    <button type="button" data-action="edit" data-id="${product.id}">수정</button>
                    <button type="button" class="delete" data-action="delete" data-id="${product.id}">판매 중지</button>
                </td>
            </tr>
        `;}).join("");
    }

    async function loadOrders() {
        const orderTable = document.querySelector("#admin-order-table");
        orderTable.innerHTML = "<tr><td colspan=\"6\">주문을 불러오는 중입니다.</td></tr>";
        try {
            state.orders = await adminFetch("/api/admin/orders");
            renderOrders();
        } catch (error) {
            orderTable.innerHTML = `<tr><td colspan="6">${escapeHtml(error.message)}</td></tr>`;
            showToast(error.message, true);
            throw error;
        }
    }

    function renderOrders() {
        const orderTable = document.querySelector("#admin-order-table");
        const query = state.orderQuery.toLowerCase();
        const orders = state.orders.filter((order) => {
            const text = `${order.orderNumber} ${order.memberUsername || ""} ${order.farmName || ""} ${order.customerName || ""}`.toLowerCase();
            return (!query || text.includes(query))
                && (state.orderStatus === "ALL" || order.status === state.orderStatus);
        });

        document.querySelector("#admin-order-count").textContent = number(state.orders.length);
        if (!orders.length) {
            orderTable.innerHTML = "<tr><td colspan=\"6\">조건에 맞는 주문이 없습니다.</td></tr>";
            return;
        }

        orderTable.innerHTML = orders.map((order) => {
            const next = nextOrderStatus[order.status];
            const itemText = (order.items || [])
                .map((item) => `${escapeHtml(item.productName)} ${item.quantity}포`)
                .join(", ") || "주문 상품 없음";
            const shippingInputs = order.status === "PREPARING"
                ? `<input data-order-carrier placeholder="배송사" value="${escapeHtml(order.trackingCarrier || "")}">
                   <input data-order-tracking placeholder="송장번호" value="${escapeHtml(order.trackingNumber || "")}">`
                : order.trackingNumber
                ? `<small>${escapeHtml(order.trackingCarrier)} · ${escapeHtml(order.trackingNumber)}</small>`
                : "";
            const operation = next
                ? `${shippingInputs}<button type="button" data-order-update="${escapeHtml(order.orderNumber)}" data-next-status="${next[0]}">${next[1]}</button>`
                : shippingInputs || "-";

            return `<tr>
                <td><strong>${escapeHtml(order.orderNumber)}</strong><small>${new Date(order.orderedAt).toLocaleString("ko-KR")}</small></td>
                <td><strong>${escapeHtml(order.farmName || order.customerName)}</strong><small>${escapeHtml(order.memberUsername || "-")} · ${escapeHtml(order.phone)}</small></td>
                <td><span class="admin-order-items">${itemText}</span></td>
                <td><strong>${number(order.totalAmount)}원</strong><small>${escapeHtml(order.paymentMethod)}</small></td>
                <td><span class="admin-status order-${String(order.status).toLowerCase()}">${escapeHtml(orderStatusLabels[order.status] || order.status)}</span></td>
                <td class="admin-order-operation">${operation}</td>
            </tr>`;
        }).join("");
    }

    async function updateOrderStatus(button) {
        const row = button.closest("tr");
        const status = button.dataset.nextStatus;
        const orderNumber = button.dataset.orderUpdate;
        const carrier = row.querySelector("[data-order-carrier]")?.value.trim() || null;
        const trackingNumber = row.querySelector("[data-order-tracking]")?.value.trim() || null;

        if (status === "SHIPPING" && (!carrier || !trackingNumber)) {
            showToast("배송사와 송장번호를 모두 입력해주세요.", true);
            return;
        }

        button.disabled = true;
        try {
            await adminFetch(
                `/api/admin/orders/${encodeURIComponent(orderNumber)}/status`,
                {
                    method: "PATCH",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ status, carrier, trackingNumber })
                }
            );
            showToast(`${orderStatusLabels[status]} 상태로 변경했습니다.`);
            await Promise.all([loadOrders(), loadDashboard(), loadActivities()]);
        } catch (error) {
            button.disabled = false;
            showToast(error.message, true);
        }
    }

    async function loadInventoryAlerts() {
        const alertTable = document.querySelector("#admin-alert-table");
        alertTable.innerHTML = "<tr><td colspan=\"7\">LOT 경고를 불러오는 중입니다.</td></tr>";
        try {
            state.alerts = await adminFetch("/api/admin/inventory/alerts");
            renderInventoryAlerts();
        } catch (error) {
            alertTable.innerHTML = `<tr><td colspan="7">${escapeHtml(error.message)}</td></tr>`;
            showToast(error.message, true);
            throw error;
        }
    }

    function renderInventoryAlerts() {
        const alertTable = document.querySelector("#admin-alert-table");
        document.querySelector("#admin-alert-count").textContent = number(state.alerts.length);
        if (!state.alerts.length) {
            alertTable.innerHTML = "<tr><td colspan=\"7\">현재 재고·유통기한 경고가 없습니다.</td></tr>";
            return;
        }

        alertTable.innerHTML = state.alerts.map((alert) => {
            const remaining = alert.daysRemaining < 0
                ? `${Math.abs(alert.daysRemaining)}일 경과`
                : `${alert.daysRemaining}일`;
            const action = alert.expired
                ? "판매 중지 후 폐기 확인"
                : alert.quantity === 0
                ? "신규 LOT 입고 필요"
                : alert.expiringSoon && alert.lowStock
                ? "우선 출고 후 재입고 검토"
                : alert.expiringSoon
                ? "FEFO 우선 출고"
                : "재입고 준비";
            const tags = [
                alert.expired ? "유통기한 만료" : alert.expiringSoon ? "유통기한 임박" : "",
                alert.lowStock ? (alert.quantity === 0 ? "품절" : "재고 부족") : ""
            ].filter(Boolean).map((tag) => `<span>${tag}</span>`).join("");

            return `<tr class="alert-${String(alert.severity).toLowerCase()}">
                <td><b class="lot-severity">${escapeHtml(alert.severity)}</b></td>
                <td><strong>${escapeHtml(alert.productName)}</strong><small>상품 #${alert.productId}</small></td>
                <td><strong>${escapeHtml(alert.lotNumber)}</strong></td>
                <td>${escapeHtml(alert.expirationDate)}</td>
                <td><strong>${remaining}</strong></td>
                <td><strong>${number(alert.quantity)}포</strong></td>
                <td><div class="lot-alert-tags">${tags}</div><small>${action}</small></td>
            </tr>`;
        }).join("");
    }

    async function loadAdminData() {
        try {
            basicAuthorization();
            await Promise.all([
                loadOrders(),
                loadInventoryAlerts(),
                loadDashboard(),
                loadActivities()
            ]);
            message.textContent = "관리자 인증이 완료되었습니다. 통계·주문·LOT·활동 이력을 불러왔습니다.";
        } catch (error) {
            message.textContent = error.message;
        }
    }

    function payloadFromForm() {
        return {
            manufacturerName: fields.manufacturerName.value.trim(),
            name: fields.productName.value.trim(),
            animalType: fields.animalType.value,
            feedStage: fields.feedStage.value.trim(),
            description: fields.description.value.trim(),
            weightKg: Number(fields.weightKg.value),
            price: Number(fields.price.value),
            originalPrice: fields.originalPrice.value ? Number(fields.originalPrice.value) : null,
            proteinPercent: Number(fields.proteinPercent.value),
            fatPercent: Number(fields.fatPercent.value),
            fiberPercent: Number(fields.fiberPercent.value),
            calciumPercent: Number(fields.calciumPercent.value),
            imageUrl: fields.imageUrl.value.trim() || null,
            badge: fields.badge.value.trim() || null,
            displayTone: fields.displayTone.value.trim(),
            displayShape: fields.displayShape.value.trim(),
            lotNumber: fields.lotNumber.value.trim(),
            manufacturedDate: fields.manufacturedDate.value,
            expirationDate: fields.expirationDate.value,
            lotQuantity: Number(fields.lotQuantity.value)
        };
    }

    function resetForm() {
        state.editingId = null;
        form.reset();
        fields.manufacturerName.value = "피드플로우 협력사";
        fields.animalType.value = "CATTLE";
        fields.weightKg.value = "25";
        fields.proteinPercent.value = "15";
        fields.fatPercent.value = "3";
        fields.fiberPercent.value = "8";
        fields.calciumPercent.value = "1";
        fields.displayTone.value = "amber";
        fields.displayShape.value = "pellet";
        fields.lotQuantity.value = "0";

        const today = new Date();
        const nextYear = new Date(today);
        nextYear.setFullYear(today.getFullYear() + 1);
        fields.manufacturedDate.value = isoDate(today);
        fields.expirationDate.value = isoDate(nextYear);
        fields.lotNumber.value = `LOT-${isoDate(today).replaceAll("-", "")}-01`;
        updateImagePreview();

        editorEyebrow.textContent = "NEW PRODUCT";
        editorTitle.textContent = "새 상품 등록";
        saveButton.textContent = "새 상품 등록";
        cancelEditButton.hidden = true;
    }

    function editProduct(productId) {
        const product = state.products.find((item) => item.id === productId);
        if (!product) {
            showToast("상품 정보를 찾을 수 없습니다.", true);
            return;
        }

        state.editingId = productId;
        fields.manufacturerName.value = product.manufacturer ?? "";
        fields.productName.value = product.name ?? "";
        fields.animalType.value = product.animalType ?? "CATTLE";
        fields.feedStage.value = product.stage ?? "";
        fields.description.value = product.description ?? "";
        fields.weightKg.value = product.weight ?? "";
        fields.price.value = product.price ?? "";
        fields.originalPrice.value = product.originalPrice ?? "";
        fields.proteinPercent.value = product.protein ?? "";
        fields.fatPercent.value = product.fat ?? "";
        fields.fiberPercent.value = product.fiber ?? "";
        fields.calciumPercent.value = product.calcium ?? "";
        fields.imageUrl.value = product.imageUrl ?? "";
        fields.badge.value = product.badge ?? "";
        fields.displayTone.value = product.tone ?? "amber";
        fields.displayShape.value = product.shape ?? "pellet";
        fields.lotNumber.value = product.lot ?? `LOT-${product.id}`;
        fields.manufacturedDate.value = product.manufacturedDate ?? isoDate(new Date());

        const defaultExpiry = new Date();
        defaultExpiry.setFullYear(defaultExpiry.getFullYear() + 1);
        fields.expirationDate.value = product.expiry ?? isoDate(defaultExpiry);
        fields.lotQuantity.value = product.stock ?? 0;
        updateImagePreview();

        editorEyebrow.textContent = `PRODUCT #${product.id}`;
        editorTitle.textContent = "상품 정보 수정";
        saveButton.textContent = "변경 내용 저장";
        cancelEditButton.hidden = false;
        form.scrollIntoView({ behavior: "smooth", block: "start" });
    }

    async function saveProduct(event) {
        event.preventDefault();
        if (!form.reportValidity()) {
            return;
        }

        let authorization;
        try {
            authorization = basicAuthorization();
        } catch (error) {
            showToast(error.message, true);
            document.querySelector("#admin-username").focus();
            return;
        }

        const editing = state.editingId !== null;
        const url = editing ? `/api/admin/products/${state.editingId}` : "/api/admin/products";
        saveButton.disabled = true;
        saveButton.textContent = editing ? "저장 중..." : "등록 중...";

        try {
            const response = await fetch(url, {
                method: editing ? "PUT" : "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": authorization
                },
                body: JSON.stringify(payloadFromForm())
            });
            if (!response.ok) {
                throw new Error(await readError(response));
            }

            showToast(editing ? "상품 정보가 수정되었습니다." : "새 상품이 등록되었습니다.");
            message.textContent = "관리자 인증과 H2 저장이 정상 작동했습니다.";
            resetForm();
            await Promise.all([loadProducts(), loadDashboard(), loadActivities()]);
        } catch (error) {
            showToast(error.message, true);
            message.textContent = error.message;
        } finally {
            saveButton.disabled = false;
            saveButton.textContent = state.editingId === null ? "새 상품 등록" : "변경 내용 저장";
        }
    }

    async function deleteProduct(productId) {
        const product = state.products.find((item) => item.id === productId);
        if (!product || !window.confirm(`'${product.name}' 상품을 판매 중지하시겠습니까?`)) {
            return;
        }

        let authorization;
        try {
            authorization = basicAuthorization();
        } catch (error) {
            showToast(error.message, true);
            return;
        }

        try {
            const response = await fetch(`/api/admin/products/${productId}`, {
                method: "DELETE",
                headers: { "Authorization": authorization }
            });
            if (!response.ok) {
                throw new Error(await readError(response));
            }
            showToast("상품 판매가 중지되었습니다.");
            if (state.editingId === productId) {
                resetForm();
            }
            await Promise.all([loadProducts(), loadDashboard(), loadActivities()]);
        } catch (error) {
            showToast(error.message, true);
        }
    }

    function updateImagePreview() {
        const preview = document.querySelector("#admin-preview-image");
        preview.src = fields.imageUrl.value.trim() || "/images/feed-bag-warehouse.png";
        preview.onerror = () => {
            preview.onerror = null;
            preview.src = "/images/feed-bag-warehouse.png";
        };
    }

    form.addEventListener("submit", saveProduct);
    cancelEditButton.addEventListener("click", resetForm);
    table.addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (!button) {
            return;
        }
        const productId = Number(button.dataset.id);
        if (button.dataset.action === "edit") {
            editProduct(productId);
        } else if (button.dataset.action === "delete") {
            deleteProduct(productId);
        }
    });
    document.querySelector("#admin-order-table").addEventListener("click", (event) => {
        const button = event.target.closest("[data-order-update]");
        if (button) {
            updateOrderStatus(button);
        }
    });
    document.querySelector("#admin-auth-load").addEventListener("click", loadAdminData);
    document.querySelector("#admin-order-refresh").addEventListener("click", loadOrders);
    document.querySelector("#admin-alert-refresh").addEventListener("click", loadInventoryAlerts);
    document.querySelector("#admin-dashboard-refresh").addEventListener("click", loadDashboard);
    document.querySelector("#admin-activity-refresh").addEventListener("click", loadActivities);
    document.querySelector("#admin-order-search").addEventListener("input", (event) => {
        state.orderQuery = event.target.value.trim();
        renderOrders();
    });
    document.querySelector("#admin-order-status-filter").addEventListener("change", (event) => {
        state.orderStatus = event.target.value;
        renderOrders();
    });

    document.querySelector("#admin-search").addEventListener("input", (event) => {
        state.query = event.target.value.trim();
        renderProducts();
    });
    document.querySelector("#admin-animal-filter").addEventListener("change", (event) => {
        state.animal = event.target.value;
        renderProducts();
    });
    document.querySelector("#admin-stock-filter").addEventListener("change", (event) => {
        state.stock = event.target.value;
        renderProducts();
    });
    document.querySelector("#admin-event-filter").addEventListener("change", (event) => {
        state.event = event.target.value;
        renderProducts();
    });
    document.querySelector("#admin-filter-reset").addEventListener("click", () => {
        state.query = "";
        state.animal = "ALL";
        state.stock = "ALL";
        state.event = "ALL";
        document.querySelector("#admin-search").value = "";
        document.querySelector("#admin-animal-filter").value = "ALL";
        document.querySelector("#admin-stock-filter").value = "ALL";
        document.querySelector("#admin-event-filter").value = "ALL";
        renderProducts();
    });
    fields.imageUrl.addEventListener("input", updateImagePreview);

    resetForm();
    loadProducts();
})();
