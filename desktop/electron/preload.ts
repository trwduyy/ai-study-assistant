import { contextBridge, ipcRenderer } from "electron";

const electronAPI = {
    getPlatform: (): Promise<string> => {
        return ipcRenderer.invoke("electron:getPlatform");
    },
    getAppVersion: (): Promise<string> => {
        return ipcRenderer.invoke("electron:getAppVersion");
    },
    openExternalUrl: (url: string): Promise<boolean> => {
        return ipcRenderer.invoke("electron:openExternalUrl", url);
    }
};

contextBridge.exposeInMainWorld("electronAPI", electronAPI);
