import {
  NativeModules,
  NativeEventEmitter,
  Platform,
  requireNativeComponent,
  type ViewStyle,
} from 'react-native';
export * from './definitions';
import type { ReactNativeIvsPlayerPlugin } from './definitions';
import React, { useImperativeHandle, useRef } from 'react';

const { IvsPlayerViewManager } = NativeModules;

// Call methods
IvsPlayerViewManager.setAutoQuality({ quality: true });

const LINKING_ERROR =
  `The package 'react-native-ivs-player' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const ReactNativeIvsPlayer = (
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

type ReactNativeIvsPlayerProps = {
  style?: ViewStyle;
};
const VIEW_NAME = 'IvsPlayerView';
const IvsPlayerView =
  requireNativeComponent<ReactNativeIvsPlayerProps>(VIEW_NAME);

const IvsPlayer = React.forwardRef<
  ReactNativeIvsPlayerPlugin,
  ReactNativeIvsPlayerProps
>((props, ref) => {
  const nativeRef = useRef(null);
  useImperativeHandle(ref, () => ReactNativeIvsPlayer);
  return <IvsPlayerView {...props} ref={nativeRef} />;
});

const { EventEmitter } = NativeModules;

const ReactNativeIvsPlayerEventEmitter = new NativeEventEmitter(EventEmitter);

export { IvsPlayer, ReactNativeIvsPlayer, ReactNativeIvsPlayerEventEmitter };
