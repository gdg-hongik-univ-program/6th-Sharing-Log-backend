(() => {
    const groupId = new URLSearchParams(window.location.search).get("groupId");
    const list = document.querySelector("#occurrence-list");
    const status = document.querySelector("#rotation-status");
    const tabs = [...document.querySelectorAll(".rotation-tab")];
    const dialog = document.querySelector("#create-chore-dialog");
    const form = document.querySelector("#create-chore-form");
    const frequencyInput = document.querySelector("#chore-frequency");
    const weeklyField = document.querySelector("#weekly-day-field");
    const biweeklyField = document.querySelector("#biweekly-anchor-field");
    const eligibilityMode = document.querySelector("#chore-eligibility-mode");
    const selectedMembersField = document.querySelector("#selected-members-field");
    const eligibleMemberList = document.querySelector("#eligible-member-list");
    const createStatus = document.querySelector("#create-chore-status");
    let frequency = "DAILY";

    if (!groupId) {
        status.textContent = "주소에 groupId가 필요합니다. 그룹 생성 화면에서 로테이션을 열어 주세요.";
        return;
    }

    document.querySelector("#open-create-chore").addEventListener("click", () => {
        dialog.showModal();
        void loadEligibleMembers();
    });
    document.querySelector("#close-create-chore").addEventListener("click", () => dialog.close());
    frequencyInput.addEventListener("change", syncScheduleFields);
    eligibilityMode.addEventListener("change", syncEligibilityField);
    tabs.forEach((tab) => tab.addEventListener("click", () => {
        frequency = tab.dataset.frequency;
        tabs.forEach((item) => item.classList.toggle("is-active", item === tab));
        void loadOccurrences();
    }));
    form.addEventListener("submit", createChore);

    document.querySelector("#chore-biweekly-anchor").value = mondayOfCurrentWeek();
    syncScheduleFields();
    syncEligibilityField();
    void loadOccurrences();

    async function loadOccurrences() {
        status.textContent = "업무를 불러오는 중...";
        list.replaceChildren();
        try {
            const response = await requestJson(
                `/api/groups/${encodeURIComponent(groupId)}/occurrences?frequency=${frequency}`
            );
            renderOccurrences(response.items);
            status.textContent = response.items.length
                ? `${response.query.activeOn} 기준 ${response.items.length}개`
                : "이 주기의 현재 업무가 없습니다.";
        } catch (error) {
            status.textContent = errorMessage(error);
        }
    }

    function renderOccurrences(items) {
        const cards = items.map((item) => {
            const card = document.createElement("article");
            card.className = `occurrence-card status-${item.status.toLowerCase()}`;

            const heading = document.createElement("div");
            heading.className = "occurrence-heading";
            const title = document.createElement("div");
            const name = document.createElement("h2");
            name.textContent = item.choreName;
            const period = document.createElement("p");
            period.textContent = `${item.periodStart} — ${item.periodEndExclusive}`;
            title.append(name, period);
            const badge = document.createElement("span");
            badge.className = "status-badge";
            badge.textContent = statusLabel(item.status);
            heading.append(title, badge);

            const assignee = document.createElement("p");
            assignee.className = "assignee-line";
            assignee.textContent = item.currentAssignee
                ? `담당 · ${item.currentAssignee.displayName}`
                : item.lastAssignee
                    ? `마지막 담당 · ${item.lastAssignee.displayName}`
                    : "담당자 없음";

            const due = document.createElement("p");
            due.className = "due-line";
            due.textContent = `마감 · ${new Date(item.dueAt).toLocaleString("ko-KR")}`;

            const actions = document.createElement("div");
            actions.className = "occurrence-actions";
            for (const action of item.availableActions) {
                const button = document.createElement("button");
                button.type = "button";
                button.textContent = actionLabel(action);
                button.addEventListener("click", () => runAction(item, action, button));
                actions.append(button);
            }

            card.append(heading, assignee, due);
            if (item.attention) {
                const attention = document.createElement("p");
                attention.className = "attention-line";
                attention.textContent = "자동 배정 가능한 멤버가 없어 관리가 필요합니다.";
                card.append(attention);
            }
            card.append(actions);
            return card;
        });
        list.replaceChildren(...cards);
    }

    async function runAction(item, action, button) {
        const paths = {
            COMPLETE: "complete",
            SKIP_ALREADY_DONE: "skip-already-done",
            DECLINE: "decline",
            RETRY_ASSIGNMENT: "retry-assignment"
        };
        let body = {};
        if (action === "SKIP_ALREADY_DONE" || action === "DECLINE") {
            body = {note: window.prompt("메모를 남길 수 있어요.", "") || null};
        }
        if (action === "RETRY_ASSIGNMENT") {
            body = {eligibilitySource: "OCCURRENCE_SNAPSHOT", sourceChoreVersion: null};
        }
        button.disabled = true;
        status.textContent = "처리 중...";
        try {
            await mutate(
                `/api/groups/${encodeURIComponent(groupId)}/occurrences/`
                    + `${encodeURIComponent(item.occurrenceId)}/${paths[action]}`,
                body,
                `"${item.version}"`
            );
            await loadOccurrences();
        } catch (error) {
            status.textContent = errorMessage(error);
            button.disabled = false;
        }
    }

    async function createChore(event) {
        event.preventDefault();
        createStatus.textContent = "생성 중...";
        const selectedFrequency = frequencyInput.value;
        const body = {
            name: document.querySelector("#chore-name").value.trim(),
            schedule: {
                frequency: selectedFrequency,
                dueTime: document.querySelector("#chore-due-time").value,
                weeklyDueDay: selectedFrequency === "WEEKLY"
                    ? document.querySelector("#chore-weekly-day").value
                    : null,
                biweeklyAnchorDate: selectedFrequency === "BIWEEKLY"
                    ? document.querySelector("#chore-biweekly-anchor").value
                    : null
            },
            eligibility: {mode: "ALL_ACTIVE_MEMBERS", membershipIds: []}
        };
        body.eligibility.mode = eligibilityMode.value;
        body.eligibility.membershipIds = eligibilityMode.value === "SELECTED_MEMBERS"
            ? [...eligibleMemberList.querySelectorAll("input:checked")].map((input) => input.value)
            : [];
        try {
            await mutate(`/api/groups/${encodeURIComponent(groupId)}/chores`, body);
            frequency = selectedFrequency;
            tabs.forEach((tab) => tab.classList.toggle(
                "is-active",
                tab.dataset.frequency === frequency
            ));
            form.reset();
            document.querySelector("#chore-due-time").value = "20:00";
            document.querySelector("#chore-biweekly-anchor").value = mondayOfCurrentWeek();
            syncScheduleFields();
            syncEligibilityField();
            dialog.close();
            await loadOccurrences();
        } catch (error) {
            createStatus.textContent = errorMessage(error);
        }
    }

    async function mutate(url, body, ifMatch) {
        const csrf = await requestJson("/api/auth/csrf");
        const headers = {
            Accept: "application/json",
            "Content-Type": "application/json",
            "Idempotency-Key": crypto.randomUUID(),
            [csrf.headerName]: csrf.token
        };
        if (ifMatch) {
            headers["If-Match"] = ifMatch;
        }
        return requestJson(url, {
            method: "POST",
            headers,
            body: JSON.stringify(body)
        });
    }

    async function requestJson(url, options = {}) {
        const response = await fetch(url, {
            credentials: "same-origin",
            cache: "no-store",
            ...options
        });
        const contentType = response.headers.get("content-type") || "";
        if (response.redirected || !contentType.toLowerCase().includes("json")) {
            throw new Error("로그인 세션이 만료되었습니다.");
        }
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.detail || `요청 실패 (${response.status})`);
        }
        return data;
    }

    function syncScheduleFields() {
        weeklyField.hidden = frequencyInput.value !== "WEEKLY";
        biweeklyField.hidden = frequencyInput.value !== "BIWEEKLY";
    }

    function syncEligibilityField() {
        selectedMembersField.hidden = eligibilityMode.value !== "SELECTED_MEMBERS";
    }

    async function loadEligibleMembers() {
        eligibleMemberList.textContent = "멤버를 불러오는 중...";
        try {
            const response = await requestJson(
                `/api/groups/${encodeURIComponent(groupId)}/rotation-members`
            );
            const labels = response.items.map((member) => {
                const label = document.createElement("label");
                const checkbox = document.createElement("input");
                checkbox.type = "checkbox";
                checkbox.value = member.membershipId;
                const text = document.createTextNode(
                    ` ${member.displayName} (${member.role})`
                );
                label.append(checkbox, text);
                return label;
            });
            eligibleMemberList.replaceChildren(...labels);
        } catch (error) {
            eligibleMemberList.textContent = errorMessage(error);
        }
    }

    function mondayOfCurrentWeek() {
        const date = new Date();
        const day = date.getDay() || 7;
        date.setDate(date.getDate() - day + 1);
        return date.toISOString().slice(0, 10);
    }

    function statusLabel(value) {
        return {
            ASSIGNED: "배정됨",
            COMPLETED: "완료",
            SKIPPED: "생략",
            NEEDS_ATTENTION: "관리 필요"
        }[value] || value;
    }

    function actionLabel(value) {
        return {
            COMPLETE: "업무 완료",
            SKIP_ALREADY_DONE: "이미 처리됨",
            DECLINE: "이번 회차는 어려워요",
            RETRY_ASSIGNMENT: "자동 배정 다시 시도"
        }[value] || value;
    }

    function errorMessage(error) {
        return error instanceof Error ? error.message : "요청 처리 중 오류가 발생했습니다.";
    }
})();
