import { contextBridge, ipcRenderer } from "electron";

const electronAPI = {
    platform: process.platform,
    getPlatform: (): Promise<string> => ipcRenderer.invoke("electron:getPlatform"),
    getAppVersion: (): Promise<string> => ipcRenderer.invoke("electron:getAppVersion"),
    openExternal: (url: string): Promise<boolean> =>
        ipcRenderer.invoke("electron:openExternal", url),
    openExternalUrl: (url: string): Promise<boolean> =>
        ipcRenderer.invoke("electron:openExternalUrl", url)
};

contextBridge.exposeInMainWorld("electronAPI", electronAPI);
