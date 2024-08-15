import {
  NativeModules,
  NativeEventEmitter,
  Platform,
  requireNativeComponent,
  type ViewStyle,
} from 'react-native';
export * from './definitions';
import type { ReactNativeIvsPlayerPlugin } from './definitions';

const { IvsPlayerViewManager } = NativeModules;

const ivsPlayerEvents = new NativeEventEmitter(IvsPlayerViewManager);

ivsPlayerEvents.addListener('onState', (event) => {
  console.log(event.state);
});

// Call methods
IvsPlayerViewManager.setAutoQuality(true);

const LINKING_ERROR =
  `The package 'react-native-ivs-player' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const CapacitorIvsPlayer = (
  IvsPlayerViewManager
    ? IvsPlayerViewManager
    : new Proxy(
        {},
        {
          get() {
            throw new Error(LINKING_ERROR);
          },
        }
      )
) as ReactNativeIvsPlayerPlugin;

type Props = {
  style: ViewStyle;
};
const IvsPlayer = requireNativeComponent<Props>('IvsPlayerView');

const { EventEmitter } = NativeModules;

const CapacitorIvsPlayerEventEmitter = new NativeEventEmitter(EventEmitter);

export { IvsPlayer, CapacitorIvsPlayer, CapacitorIvsPlayerEventEmitter };
