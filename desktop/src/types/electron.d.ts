export interface IElectronAPI {
    platform: string;
    getPlatform: () => Promise<string>;
    getAppVersion: () => Promise<string>;
    openExternal: (url: string) => Promise<boolean>;
    openExternalUrl?: (url: string) => Promise<boolean>;
}

declare global {
    interface Window {
        electronAPI?: IElectronAPI;
    }
}
