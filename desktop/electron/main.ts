import { app, BrowserWindow, shell, ipcMain } from "electron";
import path from "node:path";
import fs from "node:fs";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

let mainWindow: BrowserWindow | null = null;

const isDev = process.env.NODE_ENV === "development" || !app.isPackaged;

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

    // Gracefully show window when content is ready
    mainWindow.once("ready-to-show", () => {
        if (mainWindow) {
            mainWindow.show();
        }
    });

    // Handle opening external links in default OS browser
    mainWindow.webContents.setWindowOpenHandler(({ url }) => {
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) {
            shell.openExternal(url);
        }
        return { action: "deny" };
    });

    // Prevent in-app navigation to external origins
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
            // Invalid URL format, prevent default navigation
        }
        event.preventDefault();
        shell.openExternal(navigationUrl);
    });

    // Load dev server or production build
    if (isDev) {
        mainWindow.loadURL("http://localhost:5173");
        mainWindow.webContents.openDevTools();
    } else {
        const indexPath = path.join(__dirname, "../dist/index.html");
        mainWindow.loadFile(indexPath);
    }

    mainWindow.on("closed", () => {
        mainWindow = null;
    });
}

// IPC Handlers
ipcMain.handle("electron:getPlatform", () => {
    return process.platform;
});

ipcMain.handle("electron:getAppVersion", () => {
    return app.getVersion();
});

ipcMain.handle("electron:openExternalUrl", async (_event, url: unknown) => {
    if (typeof url === "string" && (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:"))) {
        await shell.openExternal(url);
        return true;
    }
    return false;
});

// App Lifecycle
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
