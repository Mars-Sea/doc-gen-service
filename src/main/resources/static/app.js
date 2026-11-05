/**
 * 模板管理页面逻辑
 *
 * <p>职责：把 /api/v1/template 的四个接口（list / upload / download / delete）接到界面上，
 * 处理加载、上传、下载、删除四类交互与它们的结果提示。
 *
 * <p>数据来源：全部来自同源的模板管理 API，页面自身不保存任何模板状态，
 * 每次操作成功后重新拉取列表，避免本地缓存与服务器目录不一致。
 *
 * <p>为什么用 fetch 而非直接跳转下载：下载接口的失败响应是 JSON 错误体，
 * 走 fetch 才能把 message 显示为中文提示，而不是让浏览器下载到一个错误文件。
 */

const API_BASE = "/api/v1/template";
const TEMPLATE_EXTENSIONS = [".docx", ".xlsx"];

const dropzone = document.getElementById("dropzone");
const fileInput = document.getElementById("fileInput");
const refreshBtn = document.getElementById("refreshBtn");
const tableBody = document.getElementById("tableBody");
const stateCell = document.getElementById("stateCell");
const countHint = document.getElementById("countHint");
const toast = document.getElementById("toast");

const modal = document.getElementById("modal");
const modalTitle = document.getElementById("modalTitle");
const modalText = document.getElementById("modalText");
const modalCancel = document.getElementById("modalCancel");
const modalConfirm = document.getElementById("modalConfirm");

/** 当前列表中的模板名，用于上传前判断是否会覆盖同名文件 */
let currentTemplates = [];

let toastTimer = null;
let pendingConfirm = null;

/* ---------- 提示与确认 ---------- */

function showToast(message, kind = "info") {
  toast.textContent = message;
  toast.className = "toast" + (kind === "info" ? "" : " " + kind);
  toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    toast.hidden = true;
  }, 3200);
}

/**
 * 打开确认弹层
 *
 * @param {string} title 标题
 * @param {string} text 说明文字
 * @param {() => void} onConfirm 用户点确定后的动作
 * @param {string} [confirmLabel] 确定按钮文案
 * @param {boolean} [danger] 确定按钮是否表现为危险操作
 */
function confirmAction(title, text, onConfirm, confirmLabel = "确定", danger = false) {
  modalTitle.textContent = title;
  modalText.textContent = text;
  modalConfirm.textContent = confirmLabel;
  modalConfirm.className = danger ? "btn danger" : "btn primary";
  pendingConfirm = onConfirm;
  modal.hidden = false;
  modalConfirm.focus();
}

function closeModal() {
  modal.hidden = true;
  pendingConfirm = null;
}

modalConfirm.addEventListener("click", () => {
  const action = pendingConfirm;
  closeModal();
  if (action) {
    action();
  }
});

modalCancel.addEventListener("click", closeModal);
modal.querySelectorAll("[data-modal-dismiss]").forEach((el) => el.addEventListener("click", closeModal));
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !modal.hidden) {
    closeModal();
  }
});

/* ---------- 接口错误处理 ---------- */

/**
 * 从响应体里取出可展示的中文文案
 *
 * <p>后端两类响应体：成功为 {success, message, ...}，失败为 {status, code, message}。
 * 两者都带 message，取不到时按状态码兜底，保证页面上不出现英文错误码之外的空提示。
 */
async function readErrorMessage(response) {
  let message = "";
  try {
    const text = await response.text();
    if (text) {
      const body = JSON.parse(text);
      message = typeof body.message === "string" ? body.message : "";
    }
  } catch (e) {
    message = "";
  }
  if (message) {
    return message;
  }
  const fallbacks = {
    400: "请求参数无效，请检查文件名或文件格式（HTTP 400）",
    413: "文件超过大小上限 50MB（HTTP 413）",
    422: "模板不存在或已被删除（HTTP 422）",
    500: "服务器内部错误，请查看服务日志（HTTP 500）",
  };
  return fallbacks[response.status] || `请求失败（HTTP ${response.status}）`;
}

/* ---------- 列表 ---------- */

function appendStateRow(message, isError = false) {
  tableBody.innerHTML = "";
  const row = document.createElement("tr");
  row.className = isError ? "state-row error" : "state-row";
  const cell = document.createElement("td");
  cell.colSpan = 5;
  cell.textContent = message;
  row.appendChild(cell);
  tableBody.appendChild(row);
}

/** 字节数转展示文本：单位换算属展示层职责，服务端只给字节数 */
function formatSize(bytes) {
  if (typeof bytes !== "number" || bytes < 0) {
    return "-";
  }
  if (bytes < 1024) {
    return bytes + " B";
  }
  const units = ["KB", "MB", "GB"];
  let value = bytes / 1024;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  return value.toFixed(value >= 100 ? 0 : 1) + " " + units[unitIndex];
}

/**
 * ISO-8601 UTC 转本地展示时间
 *
 * <p>服务端给的是 UTC（...Z），浏览器按本地时区渲染，跨时区的用户看到的是各自本地时刻。
 */
function formatTime(iso) {
  if (!iso) {
    return "-";
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return "-";
  }
  const pad = (n) => String(n).padStart(2, "0");
  return (
    date.getFullYear() +
    "-" +
    pad(date.getMonth() + 1) +
    "-" +
    pad(date.getDate()) +
    " " +
    pad(date.getHours()) +
    ":" +
    pad(date.getMinutes())
  );
}

function kindOf(name) {
  return name.toLowerCase().endsWith(".xlsx") ? "excel" : "word";
}

function kindLabel(name) {
  return kindOf(name) === "excel" ? "Excel" : "Word";
}

function buildRow(info) {
  // 兼容旧的纯字符串元素：服务端升级前或降级后仍只给文件名时，页面不至于整列空白
  const name = typeof info === "string" ? info : info.name;
  const tr = document.createElement("tr");

  const nameCell = document.createElement("td");
  nameCell.className = "file-name";
  nameCell.textContent = name;
  tr.appendChild(nameCell);

  const typeCell = document.createElement("td");
  const tag = document.createElement("span");
  tag.className = "tag " + kindOf(name);
  tag.textContent = kindLabel(name);
  typeCell.appendChild(tag);
  tr.appendChild(typeCell);

  const sizeCell = document.createElement("td");
  sizeCell.className = "size-cell";
  sizeCell.textContent = typeof info === "string" ? "-" : formatSize(info.size);
  tr.appendChild(sizeCell);

  const timeCell = document.createElement("td");
  timeCell.className = "time-cell";
  timeCell.textContent = typeof info === "string" ? "-" : formatTime(info.lastModified);
  tr.appendChild(timeCell);

  const opsCell = document.createElement("td");
  opsCell.className = "row-ops";

  const downloadBtn = document.createElement("button");
  downloadBtn.type = "button";
  downloadBtn.className = "btn ghost";
  downloadBtn.innerHTML =
    '<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">' +
    '<path d="M12 4v11" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />' +
    '<path d="M8 11l4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />' +
    '<path d="M5 19h14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />' +
    "</svg>下载";
  downloadBtn.addEventListener("click", () => downloadTemplate(name, downloadBtn));
  opsCell.appendChild(downloadBtn);

  const deleteBtn = document.createElement("button");

  deleteBtn.type = "button";
  deleteBtn.className = "btn danger";
  deleteBtn.innerHTML =
    '<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">' +
    '<path d="M5 7h14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />' +
    '<path d="M10 7V5h4v2" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />' +
    '<path d="M6 7l1 12a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-12" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />' +
    "</svg>删除";
  deleteBtn.addEventListener("click", () => requestDelete(name));
  opsCell.appendChild(deleteBtn);

  tr.appendChild(opsCell);
  return tr;
}

async function loadTemplates() {
  refreshBtn.disabled = true;
  countHint.textContent = "正在加载…";
  appendStateRow("正在加载模板列表…");
  try {
    const response = await fetch(`${API_BASE}/list`);
    if (!response.ok) {
      throw new Error(await readErrorMessage(response));
    }
    const body = await response.json();
    const templates = Array.isArray(body.templates) ? body.templates : [];
    // 同名判断只用到名字，这里单独存一份，避免每次上传时再从对象数组里映射
    currentTemplates = templates.map((item) => (typeof item === "string" ? item : item.name));
    countHint.textContent = templates.length ? `共 ${templates.length} 个模板` : "暂无模板";
    if (!templates.length) {
      appendStateRow("暂无模板，请先上传 .docx 或 .xlsx 文件");
      return;
    }
    tableBody.innerHTML = "";
    templates.forEach((name) => tableBody.appendChild(buildRow(name)));
  } catch (error) {
    countHint.textContent = "加载失败";
    appendStateRow(error.message || "模板列表加载失败", true);
  } finally {
    refreshBtn.disabled = false;
  }
}

/* ---------- 上传 ---------- */

function hasTemplateExtension(name) {
  const lower = name.toLowerCase();
  return TEMPLATE_EXTENSIONS.some((ext) => lower.endsWith(ext));
}

/**
 * 上传单个文件
 *
 * <p>同名判断放在前端：服务端 upload 是覆盖语义（Files.copy REPLACE_EXISTING），
 * 这里先在列表里查一遍，命中则让用户确认后再发包。
 */
function uploadFile(file) {
  if (!hasTemplateExtension(file.name)) {
    showToast("仅支持 .docx 和 .xlsx 格式的模板文件", "error");
    return;
  }

  const send = () => {
    const form = new FormData();
    form.append("file", file);
    fetch(`${API_BASE}/upload`, { method: "POST", body: form })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(await readErrorMessage(response));
        }
        return response.json();
      })
      .then((body) => {
        // 服务端会清洗文件名中的非法字符，落盘名可能与原文件名不同，这里回显真实落盘名
        const saved = body.fileName || file.name;
        showToast(`上传成功：${saved}`, "success");
        loadTemplates();
      })
      .catch((error) => showToast(error.message || "上传失败", "error"));
  };

  if (currentTemplates.includes(file.name)) {
    confirmAction(
      "覆盖已有模板",
      `模板 ${file.name} 已存在，上传后将覆盖原有文件，是否继续？`,
      send,
      "覆盖上传",
      true
    );
    return;
  }
  send();
}

function handleFiles(files) {
  Array.from(files).forEach(uploadFile);
}

dropzone.addEventListener("click", () => fileInput.click());
dropzone.addEventListener("keydown", (event) => {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    fileInput.click();
  }
});
fileInput.addEventListener("change", () => {
  handleFiles(fileInput.files);
  // 清空以便再次选择同一个文件也能触发 change
  fileInput.value = "";
});

["dragenter", "dragover"].forEach((type) =>
  dropzone.addEventListener(type, (event) => {
    event.preventDefault();
    dropzone.classList.add("dragging");
  })
);
["dragleave", "drop"].forEach((type) =>
  dropzone.addEventListener(type, (event) => {
    event.preventDefault();
    dropzone.classList.remove("dragging");
  })
);
dropzone.addEventListener("drop", (event) => {
  if (event.dataTransfer && event.dataTransfer.files.length) {
    handleFiles(event.dataTransfer.files);
  }
});

/* ---------- 下载 ---------- */

/** 从 Content-Disposition 里取出文件名，取不到则用列表里的名字兜底 */
function filenameFrom(header, fallback) {
  if (!header) {
    return fallback;
  }
  const matched = /filename="([^"]+)"/.exec(header);
  return matched ? matched[1] : fallback;
}

async function downloadTemplate(name, button) {
  button.disabled = true;
  try {
    const response = await fetch(`${API_BASE}/download/${encodeURIComponent(name)}`);
    if (!response.ok) {
      throw new Error(await readErrorMessage(response));
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filenameFrom(response.headers.get("Content-Disposition"), name);
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
    showToast(`已下载 ${name}`, "success");
  } catch (error) {
    showToast(error.message || "下载失败", "error");
  } finally {
    button.disabled = false;
  }
}

/* ---------- 删除 ---------- */

function requestDelete(name) {
  confirmAction(
    "删除模板",
    `确定删除模板 ${name} 吗？删除后不可恢复。`,
    () => {
      fetch(`${API_BASE}/${encodeURIComponent(name)}`, { method: "DELETE" })
        .then(async (response) => {
          if (!response.ok) {
            throw new Error(await readErrorMessage(response));
          }
          return response.json();
        })
        .then((body) => {
          if (body.success === false) {
            showToast(body.message || "删除失败", "error");
          } else {
            showToast(`已删除 ${name}`, "success");
          }
          loadTemplates();
        })
        .catch((error) => showToast(error.message || "删除失败", "error"));
    },
    "删除",
    true
  );
}

/* ---------- 启动 ---------- */

refreshBtn.addEventListener("click", loadTemplates);
loadTemplates();
