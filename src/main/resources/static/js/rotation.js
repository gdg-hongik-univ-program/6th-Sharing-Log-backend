(() => {
    const groupId = new URLSearchParams(window.location.search).get("groupId");
    const groupPath = groupId
        ? `/api/groups/${encodeURIComponent(groupId)}`
        : null;
    const list = document.querySelector("#occurrence-list");
    const status = document.querySelector("#rotation-status");
    const tabs = [...document.querySelectorAll(".rotation-tab")];

    const createDialog = document.querySelector("#create-chore-dialog");
    const createForm = document.querySelector("#create-chore-form");
    const frequencyInput = document.querySelector("#chore-frequency");
    const weeklyField = document.querySelector("#weekly-day-field");
    const biweeklyField = document.querySelector("#biweekly-anchor-field");
    const eligibilityMode = document.querySelector("#chore-eligibility-mode");
    const selectedMembersField = document.querySelector("#selected-members-field");
    const eligibleMemberList = document.querySelector("#eligible-member-list");
    const createStatus = document.querySelector("#create-chore-status");

    const openManageButton = document.querySelector("#open-manage-rotation");
    const manageDialog = document.querySelector("#manage-rotation-dialog");
    const manageForm = document.querySelector("#manage-rotation-form");
    const manageMemberSelect = document.querySelector("#manage-member-select");
    const manageMemberSummary = document.querySelector("#manage-member-summary");
    const manageChoreOptions = document.querySelector("#manage-chore-options");
    const manageChoreList = document.querySelector("#manage-chore-list");
    const manageStatus = document.querySelector("#manage-rotation-status");
    const saveParticipationsButton = document.querySelector("#save-member-participations");
    const removeMemberButton = document.querySelector("#remove-group-member");

    let frequency = "DAILY";
    let actorMembershipId = null;
    let canManage = false;
    let managementMembers = [];
    let managementChores = [];

    if (!groupId) {
        status.textContent = "주소에 groupId가 필요합니다. 그룹 생성 화면에서 로테이션을 열어 주세요.";
        return;
    }

    document.querySelector("#open-create-chore").addEventListener("click", () => {
        createStatus.textContent = "";
        createDialog.showModal();
        void loadEligibleMembers();
    });
    document.querySelector("#close-create-chore")
        .addEventListener("click", () => createDialog.close());
    openManageButton.addEventListener("click", () => void openManagement());
    document.querySelector("#close-manage-rotation")
        .addEventListener("click", () => manageDialog.close());
    manageMemberSelect.addEventListener("change", renderSelectedMemberManagement);
    removeMemberButton.addEventListener("click", () => void removeSelectedMember());

    frequencyInput.addEventListener("change", syncScheduleFields);
    eligibilityMode.addEventListener("change", syncEligibilityField);
    tabs.forEach((tab) => tab.addEventListener("click", () => {
        frequency = tab.dataset.frequency;
        tabs.forEach((item) => item.classList.toggle("is-active", item === tab));
        void loadOccurrences();
    }));
    createForm.addEventListener("submit", createChore);
    manageForm.addEventListener("submit", saveMemberParticipations);

    document.querySelector("#chore-biweekly-anchor").value = mondayOfCurrentWeek();
    syncScheduleFields();
    syncEligibilityField();
    void loadOccurrences();
    void loadManagementCapabilities();

    async function loadOccurrences() {
        status.textContent = "업무를 불러오는 중...";
        list.replaceChildren();
        try {
            const response = await requestJson(
                `${groupPath}/occurrences?frequency=${encodeURIComponent(frequency)}`
            );
            const items = Array.isArray(response.items) ? response.items : [];
            renderOccurrences(items);
            status.textContent = items.length
                ? `${response.query.activeOn} 기준 ${items.length}개`
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
            for (const action of item.availableActions || []) {
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
                `${groupPath}/occurrences/${encodeURIComponent(item.occurrenceId)}/${paths[action]}`,
                {
                    method: "POST",
                    body,
                    ifMatch: strongEtag(item.version)
                }
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
            ? [...eligibleMemberList.querySelectorAll("input:checked")]
                .map((input) => input.value)
            : [];
        try {
            await mutate(`${groupPath}/chores`, {method: "POST", body});
            frequency = selectedFrequency;
            tabs.forEach((tab) => tab.classList.toggle(
                "is-active",
                tab.dataset.frequency === frequency
            ));
            createForm.reset();
            document.querySelector("#chore-due-time").value = "20:00";
            document.querySelector("#chore-biweekly-anchor").value = mondayOfCurrentWeek();
            syncScheduleFields();
            syncEligibilityField();
            createDialog.close();
            managementChores = [];
            await loadOccurrences();
        } catch (error) {
            createStatus.textContent = errorMessage(error);
        }
    }

    async function loadManagementCapabilities() {
        try {
            const response = await requestJson(`${groupPath}/rotation-members`);
            applyMemberResponse(response);
        } catch {
            openManageButton.hidden = true;
        }
    }

    function applyMemberResponse(response) {
        actorMembershipId = response.actorMembershipId || null;
        canManage = response.canManage === true;
        managementMembers = Array.isArray(response.items) ? response.items : [];
        openManageButton.hidden = !canManage;
    }

    async function openManagement() {
        if (!canManage) {
            return;
        }
        manageStatus.textContent = "";
        manageDialog.showModal();
        await loadManagementData(manageMemberSelect.value || actorMembershipId);
    }

    async function loadManagementData(preferredMembershipId) {
        manageStatus.textContent = "멤버와 업무를 불러오는 중...";
        manageMemberSelect.disabled = true;
        saveParticipationsButton.disabled = true;
        removeMemberButton.disabled = true;
        manageChoreOptions.textContent = "참여 업무를 불러오는 중...";
        manageChoreList.textContent = "업무를 불러오는 중...";
        try {
            const [memberResponse, choreResponse] = await Promise.all([
                requestJson(`${groupPath}/rotation-members`),
                requestJson(`${groupPath}/chores?active=all`)
            ]);
            applyMemberResponse(memberResponse);
            managementChores = Array.isArray(choreResponse.items) ? choreResponse.items : [];

            if (!canManage) {
                manageMemberSelect.replaceChildren();
                manageMemberSummary.textContent = "";
                manageChoreOptions.textContent = "로테이션 관리 권한이 없습니다.";
                manageChoreList.textContent = "로테이션 관리 권한이 없습니다.";
                manageStatus.textContent = "로테이션 관리 권한이 없습니다.";
                return;
            }

            const activeMembers = selectableMembers();
            const selectedMembershipId = activeMembers.some(
                (member) => member.membershipId === preferredMembershipId
            )
                ? preferredMembershipId
                : activeMembers[0]?.membershipId;
            renderMemberSelect(activeMembers, selectedMembershipId);
            renderSelectedMemberManagement();
            renderChoreManagementList();
            manageStatus.textContent = activeMembers.length
                ? ""
                : "관리할 활성 멤버가 없습니다.";
        } catch (error) {
            manageMemberSelect.replaceChildren();
            manageMemberSummary.textContent = "";
            manageChoreOptions.textContent = "참여 업무를 불러오지 못했습니다.";
            manageChoreList.textContent = "업무를 불러오지 못했습니다.";
            manageStatus.textContent = errorMessage(error);
        } finally {
            manageMemberSelect.disabled = selectableMembers().length === 0;
            saveParticipationsButton.disabled = selectableMembers().length === 0;
            removeMemberButton.disabled = selectableMembers().length === 0;
        }
    }

    function selectableMembers() {
        return managementMembers.filter(
            (member) => !member.status || member.status === "ACTIVE"
        );
    }

    function renderMemberSelect(members, selectedMembershipId) {
        const options = members.map((member) => {
            const option = document.createElement("option");
            option.value = member.membershipId;
            option.textContent = `${member.displayName} (${roleLabel(member.role)})`;
            option.selected = member.membershipId === selectedMembershipId;
            return option;
        });
        if (!options.length) {
            const option = document.createElement("option");
            option.textContent = "관리할 활성 멤버가 없습니다.";
            option.disabled = true;
            option.selected = true;
            options.push(option);
        }
        manageMemberSelect.replaceChildren(...options);
    }

    function selectedManagementMember() {
        return selectableMembers().find(
            (member) => member.membershipId === manageMemberSelect.value
        ) || null;
    }

    function renderSelectedMemberManagement() {
        const member = selectedManagementMember();
        if (!member) {
            manageMemberSummary.textContent = "";
            manageChoreOptions.textContent = "관리할 활성 멤버가 없습니다.";
            saveParticipationsButton.disabled = true;
            removeMemberButton.disabled = true;
            return;
        }

        const activeChores = managementChores.filter((chore) => chore.active !== false);
        const participationCount = activeChores.filter(
            (chore) => memberParticipates(chore, member.membershipId)
        ).length;
        manageMemberSummary.textContent =
            `${member.displayName} · ${roleLabel(member.role)} · 활성 업무 ${participationCount}개 참여`;
        removeMemberButton.textContent = member.membershipId === actorMembershipId
            ? "그룹에서 탈퇴"
            : "그룹에서 내보내기";
        saveParticipationsButton.disabled = false;
        removeMemberButton.disabled = false;

        if (!activeChores.length) {
            manageChoreOptions.textContent = "참여 설정을 변경할 활성 업무가 없습니다.";
            return;
        }

        const labels = activeChores.map((chore, index) => {
            const label = document.createElement("label");
            const checkbox = document.createElement("input");
            checkbox.type = "checkbox";
            checkbox.id = `manage-chore-option-${index}`;
            checkbox.value = chore.choreId;
            checkbox.checked = memberParticipates(chore, member.membershipId);

            const copy = document.createElement("span");
            copy.className = "choice-copy";
            const name = document.createElement("strong");
            name.textContent = chore.name;
            const schedule = document.createElement("small");
            schedule.textContent = frequencyLabel(chore.schedule?.frequency);
            copy.append(name, schedule);
            label.append(checkbox, copy);
            return label;
        });
        manageChoreOptions.replaceChildren(...labels);
    }

    function renderChoreManagementList() {
        if (!managementChores.length) {
            manageChoreList.textContent = "등록된 업무가 없습니다.";
            return;
        }

        const rows = managementChores.map((chore) => {
            const row = document.createElement("div");
            row.className = "chore-management-row";
            const copy = document.createElement("div");
            const name = document.createElement("h4");
            name.textContent = chore.name;
            const meta = document.createElement("p");
            meta.className = "chore-management-meta";
            meta.textContent = `${frequencyLabel(chore.schedule?.frequency)} · `
                + `${chore.active === false ? "비활성" : "활성"}`;
            copy.append(name, meta);
            row.append(copy);

            if (chore.active === false) {
                const badge = document.createElement("span");
                badge.className = "inactive-badge";
                badge.textContent = "비활성";
                row.append(badge);
            } else {
                const button = document.createElement("button");
                button.type = "button";
                button.className = "danger-outline-button";
                button.textContent = "업무 비활성화";
                button.setAttribute("aria-label", `${chore.name} 업무 비활성화`);
                button.addEventListener(
                    "click",
                    () => void deactivateChore(chore, button)
                );
                row.append(button);
            }
            return row;
        });
        manageChoreList.replaceChildren(...rows);
    }

    function memberParticipates(chore, membershipId) {
        const membershipIds = eligibilityMembershipIds(chore);
        return membershipIds.includes(membershipId);
    }

    function eligibilityMembershipIds(chore) {
        if (Array.isArray(chore.eligibility?.membershipIds)) {
            return chore.eligibility.membershipIds;
        }
        if (Array.isArray(chore.eligibility?.members)) {
            return chore.eligibility.members
                .map((member) => member.membershipId)
                .filter(Boolean);
        }
        return [];
    }

    async function saveMemberParticipations(event) {
        event.preventDefault();
        const member = selectedManagementMember();
        if (!member) {
            manageStatus.textContent = "관리할 멤버를 선택해 주세요.";
            return;
        }

        const checkedChoreIds = new Set(
            [...manageChoreOptions.querySelectorAll("input:checked")]
                .map((input) => input.value)
        );
        const addChoreIds = [];
        const removeChoreIds = [];
        const expectedVersions = {};
        for (const chore of managementChores.filter((item) => item.active !== false)) {
            const participated = memberParticipates(chore, member.membershipId);
            const shouldParticipate = checkedChoreIds.has(chore.choreId);
            if (participated === shouldParticipate) {
                continue;
            }
            (shouldParticipate ? addChoreIds : removeChoreIds).push(chore.choreId);
            expectedVersions[chore.choreId] = chore.version;
        }

        if (!addChoreIds.length && !removeChoreIds.length) {
            manageStatus.textContent = "변경된 참여 업무가 없습니다.";
            return;
        }

        const applicationScope = manageForm.querySelector(
            'input[name="application-scope"]:checked'
        ).value;
        saveParticipationsButton.disabled = true;
        manageMemberSelect.disabled = true;
        manageStatus.textContent = "참여 업무를 변경하는 중...";
        try {
            await mutate(
                `${groupPath}/rotation-members/`
                    + `${encodeURIComponent(member.membershipId)}/chore-participations`,
                {
                    method: "PATCH",
                    body: {
                        addChoreIds,
                        removeChoreIds,
                        applicationScope,
                        expectedVersions
                    }
                }
            );
            await Promise.all([
                loadOccurrences(),
                loadManagementData(member.membershipId)
            ]);
            const scopeText = applicationScope === "CURRENT_AND_FUTURE"
                ? "현재 회차부터"
                : "다음 회차부터";
            manageStatus.textContent =
                `${scopeText} 적용했습니다. 추가 ${addChoreIds.length}개, 제외 ${removeChoreIds.length}개`;
        } catch (error) {
            manageStatus.textContent = errorMessage(error);
        } finally {
            manageMemberSelect.disabled = selectableMembers().length === 0;
            saveParticipationsButton.disabled = selectableMembers().length === 0;
        }
    }

    async function removeSelectedMember() {
        const member = selectedManagementMember();
        if (!member) {
            manageStatus.textContent = "관리할 멤버를 선택해 주세요.";
            return;
        }

        const self = member.membershipId === actorMembershipId;
        const confirmed = window.confirm(
            self
                ? "그룹에서 탈퇴할까요?\n현재 담당 중인 모든 미완료 업무는 즉시 재배정됩니다."
                : `${member.displayName}님을 그룹에서 내보낼까요?\n`
                    + "현재 담당 중인 모든 미완료 업무는 즉시 재배정됩니다."
        );
        if (!confirmed) {
            return;
        }

        removeMemberButton.disabled = true;
        saveParticipationsButton.disabled = true;
        manageStatus.textContent = self ? "그룹에서 탈퇴하는 중..." : "멤버를 내보내는 중...";
        try {
            const response = await mutate(
                `${groupPath}/members/${encodeURIComponent(member.membershipId)}/leave`,
                {
                    method: "POST",
                    body: {},
                    ifMatch: strongEtag(member.version)
                }
            );
            const message = leaveResultMessage(response, member.displayName, self);
            if (self) {
                canManage = false;
                openManageButton.hidden = true;
                manageDialog.close();
                status.textContent = message;
                return;
            }
            await Promise.all([
                loadOccurrences(),
                loadManagementData(actorMembershipId)
            ]);
            manageStatus.textContent = message;
        } catch (error) {
            manageStatus.textContent = errorMessage(error);
        } finally {
            removeMemberButton.disabled = selectableMembers().length === 0;
            saveParticipationsButton.disabled = selectableMembers().length === 0;
        }
    }

    function leaveResultMessage(response, displayName, self) {
        const base = self
            ? "그룹에서 탈퇴했습니다."
            : `${displayName}님을 그룹에서 내보냈습니다.`;
        const summary = response?.reassignmentSummary;
        if (!summary || !summary.processedCount) {
            return base;
        }
        return `${base} 미완료 업무 ${summary.processedCount}건 중 `
            + `${summary.reassignedCount}건 재배정, `
            + `${summary.needsAttentionCount}건 관리 필요`;
    }

    async function deactivateChore(chore, button) {
        const confirmed = window.confirm(
            `${chore.name} 업무를 비활성화할까요?\n`
                + "새 회차 생성은 중단되지만 이미 생성된 회차와 과거 이력은 유지됩니다."
        );
        if (!confirmed) {
            return;
        }

        button.disabled = true;
        manageStatus.textContent = "업무를 비활성화하는 중...";
        try {
            await mutate(
                `${groupPath}/chores/${encodeURIComponent(chore.choreId)}`,
                {
                    method: "DELETE",
                    ifMatch: strongEtag(chore.version)
                }
            );
            await Promise.all([
                loadOccurrences(),
                loadManagementData(manageMemberSelect.value)
            ]);
            manageStatus.textContent =
                `${chore.name} 업무를 비활성화했습니다. 이미 생성된 회차는 유지됩니다.`;
        } catch (error) {
            manageStatus.textContent = errorMessage(error);
            button.disabled = false;
        }
    }

    async function mutate(url, {method = "POST", body, ifMatch} = {}) {
        const csrf = await requestJson("/api/auth/csrf");
        const headers = {
            Accept: "application/json",
            "Idempotency-Key": crypto.randomUUID(),
            [csrf.headerName]: csrf.token
        };
        if (body !== undefined) {
            headers["Content-Type"] = "application/json";
        }
        if (ifMatch) {
            headers["If-Match"] = ifMatch;
        }
        return requestJson(url, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
    }

    async function requestJson(url, options = {}) {
        const response = await fetch(url, {
            credentials: "same-origin",
            cache: "no-store",
            ...options
        });
        if (response.redirected) {
            throw new Error("로그인 세션이 만료되었습니다.");
        }
        if (response.status === 204 || response.status === 205) {
            if (!response.ok) {
                throw new Error(`요청 실패 (${response.status})`);
            }
            return null;
        }

        const contentType = response.headers.get("content-type") || "";
        if (!contentType.toLowerCase().includes("json")) {
            if (!response.ok) {
                throw new Error(`요청 실패 (${response.status})`);
            }
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
            const response = await requestJson(`${groupPath}/rotation-members`);
            applyMemberResponse(response);
            const items = selectableMembers();
            if (!items.length) {
                eligibleMemberList.textContent = "담당 가능한 활성 멤버가 없습니다.";
                return;
            }
            const labels = items.map((member) => {
                const label = document.createElement("label");
                const checkbox = document.createElement("input");
                checkbox.type = "checkbox";
                checkbox.value = member.membershipId;
                const text = document.createElement("span");
                text.textContent = `${member.displayName} (${roleLabel(member.role)})`;
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

    function strongEtag(version) {
        return version === undefined || version === null ? null : `"${version}"`;
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

    function frequencyLabel(value) {
        return {
            DAILY: "매일",
            WEEKLY: "매주",
            BIWEEKLY: "격주"
        }[value] || value || "주기 미정";
    }

    function roleLabel(value) {
        return {
            OWNER: "소유자",
            MEMBER: "멤버"
        }[value] || value;
    }

    function errorMessage(error) {
        return error instanceof Error ? error.message : "요청 처리 중 오류가 발생했습니다.";
    }
})();
