declare module 'react-native-nordic-dfu' {
  export class NordicDFU {
    static startDFU({
      deviceAddress,
      deviceName,
      filePath,
      keepBond,
      alternativeAdvertisingNameEnabled,
      forceScanForNewAddress
    }: {
      deviceAddress: string;
      deviceName?: string;
      filePath: string | null;
      keepBond?: boolean;
      alternativeAdvertisingNameEnabled?: boolean;
      forceScanForNewAddress?: boolean;
    }): Promise<string>;
  }

  export interface IDfuUpdate {
    percent?: number;
    currentPart?: number;
    partsTotal?: number;
    avgSpeed?: number;
    speed?: number;
    state?: string;
    deviceAddress?: string;
    level?: number;
    message?: string;
  }

  export class DFUEmitter {
    static addListener(
      name: 'DFUProgress' | 'DFUStateChanged' | 'DFULogEvent',
      handler: (update: IDfuUpdate) => void
    ): void;

    static removeAllListeners(name: 'DFUProgress' | 'DFUStateChanged' | 'DFULogEvent'): void;
  }
}
