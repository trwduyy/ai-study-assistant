import { app, BrowserWindow, shell, ipcMain } from "electron";
import path from "node:path";
import fs from "node:fs";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

let mainWindow: BrowserWindow | null = null;

const isDev = process.env.NODE_ENV === "development" || !app.isPackaged;

function isSafeHttpUrl(url: unknown): url is string {
    if (typeof url !== "string" || !url.trim()) {
        return false;
    }
    try {
        const parsed = new URL(url);
        return parsed.protocol === "http:" || parsed.protocol === "https:";
    } catch {
        return false;
    }
}

function resolvePreloadPath(): string {
    const candidateCjs = path.join(__dirname, "preload.cjs");
    if (fs.existsSync(candidateCjs)) {
        return candidateCjs;
    }
    return path.join(__dirname, "preload.js");
}

function createWindow(): void {
    mainWindow = new BrowserWindow({
        width: 1280,
        height: 800,
        minWidth: 1024,
        minHeight: 680,
        autoHideMenuBar: true,
        show: false,
        webPreferences: {
            preload: resolvePreloadPath(),
            contextIsolation: true,
            nodeIntegration: false,
            sandbox: true
        }
    });

    mainWindow.once("ready-to-show", () => {
        if (mainWindow) {
            mainWindow.show();
        }
    });

    // Security: Open target="_blank" and external http/https links in OS default browser
    mainWindow.webContents.setWindowOpenHandler(({ url }) => {
        if (isSafeHttpUrl(url)) {
            void shell.openExternal(url);
        }
        return { action: "deny" };
    });

    // Security: Prevent in-app navigation to external origins; open http/https externally
    mainWindow.webContents.on("will-navigate", (event, navigationUrl) => {
        try {
            const parsedUrl = new URL(navigationUrl);
            if (isDev && parsedUrl.host === "localhost:5173") {
                return;
            }
            if (!isDev && navigationUrl.startsWith("file://")) {
                return;
            }
        } catch {
            // Invalid URL format; block navigation
        }
        event.preventDefault();
        if (isSafeHttpUrl(navigationUrl)) {
            void shell.openExternal(navigationUrl);
        }
    });

    if (isDev) {
        void mainWindow.loadURL("http://localhost:5173");
        mainWindow.webContents.openDevTools();
    } else {
        const indexPath = path.join(__dirname, "../dist/index.html");
        void mainWindow.loadFile(indexPath);
    }

    mainWindow.on("closed", () => {
        mainWindow = null;
    });
}

// IPC Handlers
ipcMain.handle("electron:getPlatform", () => process.platform);
ipcMain.handle("electron:getAppVersion", () => app.getVersion());

async function handleOpenExternal(_event: Electron.IpcMainInvokeEvent, url: unknown): Promise<boolean> {
    if (isSafeHttpUrl(url)) {
        await shell.openExternal(url);
        return true;
    }
    return false;
}

ipcMain.handle("electron:openExternal", handleOpenExternal);
ipcMain.handle("electron:openExternalUrl", handleOpenExternal);

// Standard lifecycle for Windows, Linux, and macOS
app.whenReady().then(() => {
    createWindow();

    app.on("activate", () => {
        if (BrowserWindow.getAllWindows().length === 0) {
            createWindow();
        }
    });
});

app.on("window-all-closed", () => {
    if (process.platform !== "darwin") {
        app.quit();
    }
});
