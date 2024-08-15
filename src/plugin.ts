import { NativeModules, NativeEventEmitter } from 'react-native';
import type {
  ReactNativeIvsPlayerState,
  ReactNativeIvsPlayerPlugin,
  ReactNativeIvsPlayerBackgroundState,
  ReactNativeIvsPlayerCreateOptions,
  ReactNativeIvsPlayerCastStatus,
  ReactNativeIvsPlayerEvent,
} from './definitions';

const { IvsPlayerViewManager } = NativeModules;

const ivsPlayerEvents = new NativeEventEmitter(IvsPlayerViewManager);

ivsPlayerEvents.addListener('onState', (event) => {
    console.log(event.state);
});

// Call methods
IvsPlayerViewManager.setAutoQuality(true);

export type ListenerCallback = (err: any, ...args: any[]) => void;
export interface Plugin {
  addListener(
    eventName: string,
    listenerFunc: (...args: any[]) => any
  ): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}
export interface PluginListenerHandle {
  remove: () => Promise<void>;
}

export class ReactNativeIvsPlayer
// extends IvsPlayerViewManager
  implements ReactNativeIvsPlayerPlugin
{
  addListener(
    eventName: ReactNativeIvsPlayerEvent,
    listenerFunc: ListenerCallback
  ): Promise<PluginListenerHandle> & PluginListenerHandle {
    console.log('addListener', eventName, listenerFunc);
    return super.addListener(eventName, listenerFunc);
  }
  removeAllListeners(): Promise<void> {
    console.log('removeAllListeners');
    return super.removeAllListeners();
  }
  async create(options: ReactNativeIvsPlayerCreateOptions): Promise<void> {
    console.log('create', options);
    return;
  }
  async start(): Promise<void> {
    console.log('start');
    return;
  }
  async pause(): Promise<void> {
    console.log('pause');
    return;
  }
  async cast(): Promise<void> {
    console.log('cast');
    return;
  }
  async getCastStatus(): Promise<ReactNativeIvsPlayerCastStatus> {
    console.log('getCastStatus');
    return {
      channelSlug: '',
      streamId: '',
      isActive: false,
      routeName: '',
      connectionState: 'disconnected',
      hasVideoCapableRoutes: false,
      muted: false,
    };
  }
  async getUrl(): Promise<{ url: string }> {
    console.log('getUrl');
    return { url: '' };
  }
  async getState(): Promise<{ state: ReactNativeIvsPlayerState }> {
    console.log('getState');
    return { state: 'UNKNOWN' };
  }
  async setPlayerPosition(options: { toBack: boolean }): Promise<void> {
    console.log('setPlayerPosition', options);
    return;
  }
  async getPlayerPosition(): Promise<{ toBack: boolean }> {
    console.log('getPlayerPosition');
    return { toBack: false };
  }
  async delete(): Promise<void> {
    console.log('delete');
    return;
  }
  async setFrame(options: {
    x: number;
    y: number;
    width: number;
    height: number;
  }): Promise<void> {
    console.log('setPosition', options);
    return;
  }
  async getFrame(): Promise<{
    x: number;
    y: number;
    width: number;
    height: number;
  }> {
    console.log('getPosition');
    return { x: 0, y: 0, width: 0, height: 0 };
  }
  async setBackgroundState(options: {
    backgroundState: ReactNativeIvsPlayerBackgroundState;
  }): Promise<void> {
    console.log('setBackgroundState', options);
    return;
  }
  async getBackgroundState(): Promise<{
    backgroundState: ReactNativeIvsPlayerBackgroundState;
  }> {
    console.log('getBackgroundState');
    return { backgroundState: 'PAUSED' };
  }
  async setPip(): Promise<void> {
    console.log('setPip');
  }
  async getPip(): Promise<{ pip: boolean }> {
    console.log('getPip');
    return { pip: false };
  }
  async setFullscreen(): Promise<void> {
    console.log('toggleFullscreen');
    return;
  }
  async getFullscreen(): Promise<{ fullscreen: boolean }> {
    console.log('getFullscreen');
    return { fullscreen: false };
  }
  async setMute(): Promise<void> {
    console.log('toggleMute');
    return;
  }
  async getMute(): Promise<{ mute: boolean }> {
    console.log('getMute');
    return { mute: false };
  }
  async setQuality(options: { quality: string }): Promise<void> {
    console.log('setQuality', options);
    return;
  }
  async getQualities(): Promise<{ qualities: string[] }> {
    console.log('getQualities');
    return { qualities: [] };
  }
  async getQuality(): Promise<{ quality: string }> {
    console.log('getQuality');
    return { quality: '' };
  }
  async setAutoQuality(options: { autoQuality: boolean }): Promise<void> {
    console.log('setAutoQuality', options);
    return;
  }
  async getAutoQuality(): Promise<{ autoQuality: boolean }> {
    console.log('getAutoQuality');
    return { autoQuality: false };
  }
  async getPluginVersion(): Promise<{ version: string }> {
    console.warn('Cannot get plugin version in web');
    return { version: 'default' };
  }
  async seekTo(options: { position: number }): Promise<void> {
    console.log('seekTo: ', options);
    return;
  }
  async getSeekPosition(): Promise<{ position: number }> {
    console.log('getSeekPosition');
    return { position: 0 };
  }
  async setPlaybackRate(options: { playbackRate: number }): Promise<void> {
    console.log('setPlaybackRate: ', options);
    return;
  }
  async getPlaybackRate(): Promise<{ playbackRate: number }> {
    console.log('getPlaybackRate');
    return { playbackRate: 1.0 };
  }
  async updatePlayerSrcUrl(options: { url: string }): Promise<void> {
    console.log('updatePlayerSrcUrl', options);
    return;
  }
}
