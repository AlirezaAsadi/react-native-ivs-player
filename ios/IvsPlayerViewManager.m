#import <React/RCTViewManager.h>
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(IvsPlayerViewManager, RCTViewManager)

//RCT_EXPORT_VIEW_PROPERTY(color, NSString)
RCT_EXTERN_METHOD(setAutoQuality:(BOOL)autoQuality resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(setFrame:(NSDictionary)options resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(create:(NSDictionary)options resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

@end
@interface RCT_EXTERN_MODULE(EventEmitter, RCTEventEmitter)


@end
