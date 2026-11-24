export interface IElectronAPI {
    getPlatform: () => Promise<string>;
    getAppVersion: () => Promise<string>;
    openExternalUrl: (url: string) => Promise<boolean>;
}

declare global {
    interface Window {
        electronAPI?: IElectronAPI;
    }
}
