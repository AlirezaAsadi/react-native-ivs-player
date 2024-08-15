import Foundation
import AmazonIVSPlayer
import UIKit
import AVKit
import MediaPlayer

func stateToStateName (_ state: IVSPlayer.State) -> String {
    switch state {
    case .idle:
        return "IDLE"
    case .buffering:
        return "BUFFERING"
    case .ready:
        return "READY"
    case .playing:
        return "PLAYING"
    case .ended:
        return "ENDED"
    @unknown default:
        return "UNKNOWN"
    }
}

@objc(EventEmitter)
class EventEmitter: RCTEventEmitter {
  
  private static var eventEmitter: RCTEventEmitter?

  override init() {
    super.init()
    EventEmitter.eventEmitter = self
  }

public override func supportedEvents() -> [String]! {
    return ["onState", "onCastStatus", "startPip", "stopPip", "expandPip", "closePip", "onCastStatus", "onRebuffer", "onSeekCompleted", "onVideoSize", "onQuality", "onError", "onDuration", "onCue", "onState"]
}

  @objc
  override static func requiresMainQueueSetup() -> Bool {
    return true
  }
  
  func sendEvent(withName: String, body: [String: Any]?) {
    EventEmitter.eventEmitter?.sendEvent(withName: withName, body: body)
  }
}


class CapacitorIVSPlayer: UIView, IVSPlayer.Delegate {

    var capacitorPlugin: IvsPlayerViewManager!
    let eventEmitter = EventEmitter()
    
    @objc func addPlayerView() {
        print("CapacitorIVSPlayer addPlayerView")
        DispatchQueue.main.async {
          print("CapacitorIVSPlayer addPlayerView:main")
          if let rootViewController = UIApplication.shared.keyWindow?.rootViewController {
              let playerView = self.capacitorPlugin.playerView // Your playerView
            // Customize your playerView here
              print("CapacitorIVSPlayer addPlayerView:main:xxx1")
            rootViewController.view.addSubview(playerView)
              print("CapacitorIVSPlayer addPlayerView:main:xxx2")
              
//              capacitorPlugin.bridge?.viewController?.view.addSubview(capacitorPlugin.playerView)
//              capacitorPlugin.applyLastSeekPosition()
          }
            print("CapacitorIVSPlayer addPlayerView:main:settingViewController")
           self.capacitorPlugin.viewController = UIApplication.shared.keyWindow?.rootViewController;
        }
      }
    
    

    func player(_ player: IVSPlayer, didChangeState state: IVSPlayer.State) {
        print("CapacitorIVSPlayer state change \(state)")
        let stateName = stateToStateName(state)
        print("CapacitorIVSPlayer \(stateName)")
        if state == .ready && capacitorPlugin.autoPlay &&
            !capacitorPlugin.isCastActive {
            capacitorPlugin.player.play()
        }
        // when playing add to view
        if state == .playing {
            //capacitorPlugin.bridge?.viewController?.view.addSubview(capacitorPlugin.playerView)
//            self.capacitorPlugin?.playerView.addSubview(capacitorPlugin.playerView)
////            addPlayerView()
            self.capacitorPlugin.viewController?.view.addSubview(capacitorPlugin.playerView)
            capacitorPlugin.applyLastSeekPosition()
        }
        eventEmitter.sendEvent(withName: "onState", body: ["state": stateName])
    }

    func player(_ player: IVSPlayer, didOutputCue cue: IVSCue) {
        eventEmitter.sendEvent(withName: "onCue", body: ["cue": cue])
    }

    func player(_ player: IVSPlayer, didChangeDuration duration: CMTime) {
        eventEmitter.sendEvent(withName: "onDuration", body: ["duration": duration.seconds])
    }

    func player(_ player: IVSPlayer, didFailWithError error: Error) {
        eventEmitter.sendEvent(withName: "onError", body: ["error": error.localizedDescription])
    }

    func playerWillRebuffer(_ player: IVSPlayer) {
        eventEmitter.sendEvent(withName: "onRebuffer", body: [:])
    }
    func player(_ player: IVSPlayer, didSeekTo time: CMTime) {
        eventEmitter.sendEvent(withName: "onSeekCompleted", body: ["position": time.seconds])
    }
    func player(_ player: IVSPlayer, didChangeVideoSize videoSize: CGSize) {
        eventEmitter.sendEvent(withName: "onVideoSize", body: ["videoSize": videoSize])
    }

    func player(_ player: IVSPlayer, didChangeQuality quality: IVSQuality?) {
        eventEmitter.sendEvent(withName: "onQuality", body: ["quality": quality?.name ?? ""])
    }

}


class TouchThroughView: IVSPlayerView {
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let view = super.hitTest(point, with: event)
        return view == self ? nil : view
    }
}

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */


@objc(IvsPlayerViewManager)
class IvsPlayerViewManager: RCTViewManager, AVPictureInPictureControllerDelegate {
    
    private let PLUGIN_VERSION = "0.13.34"
    

    var viewController: UIViewController?
    let player = IVSPlayer()
    let playerDelegate = CapacitorIVSPlayer()
    let playerView = TouchThroughView()
    private var _pipController: Any?
    private var isFScreen = false
    private var originalFrame: CGRect?
    private var originalParent: UIView?
    private var airplayButton = AVRoutePickerView()
    var didRestorePiP: Bool = false
    var isClosed: Bool = true
    var toBack: Bool = false
    var autoPlay: Bool = false
    var isCastActive: Bool = false
    var avPlayer: AVPlayer?
    var backgroundState: String = "PAUSED"
    var lastForegroundEvent: Date = Date();
    var lastSeekPosBeforeSrcChange: CMTime? = nil
    func hexStringToUIColor(hexColor: String) -> UIColor {
        let stringScanner = Scanner(string: hexColor)

        if(hexColor.hasPrefix("#")) {
          stringScanner.scanLocation = 1
        }
        var color: UInt32 = 0
        stringScanner.scanHexInt32(&color)

        let r = CGFloat(Int(color >> 16) & 0x000000FF)
        let g = CGFloat(Int(color >> 8) & 0x000000FF)
        let b = CGFloat(Int(color) & 0x000000FF)

        return UIColor(red: r / 255.0, green: g / 255.0, blue: b / 255.0, alpha: 1)
      }
    
    override func view() -> CapacitorIVSPlayer {
        var picture = self.playerDelegate
        picture.backgroundColor = self.hexStringToUIColor(hexColor: "#0000ff")
    //     return v;
        return picture
    }
    

    override init() {
        super.init()
        print("CapacitorIVSPlayer load")
        viewController?.view?.backgroundColor = UIColor.black
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("CapacitorIVSPlayer ‼️ Could not setup AVAudioSession: \(error)")
        }
        playerDelegate.capacitorPlugin = self
        NotificationCenter.default.addObserver(self, selector: #selector(applicationDidBecomeActive(notification:)), name: UIApplication.didBecomeActiveNotification, object: nil)
        
        NotificationCenter.default.addObserver(self, selector: #selector(applicationWillEnterForeground(notification:)), name: UIApplication.willEnterForegroundNotification, object: nil)

        NotificationCenter.default.addObserver(self, selector: #selector(applicationDidEnterBackground(_ :)), name: UIApplication.didEnterBackgroundNotification, object: nil)

        NotificationCenter.default.addObserver(self, selector: #selector(deviceWillLock), name: UIApplication.protectedDataWillBecomeUnavailableNotification, object: nil)

        let routeChangeNotification = AVAudioSession.routeChangeNotification
        NotificationCenter.default.addObserver(self, selector: #selector(handleAudioRouteChange(_:)), name: routeChangeNotification, object: nil)

        player.delegate = playerDelegate
        self.playerView.player = self.player
        self.preparePictureInPicture()
    }

    func createAvPlayer(url: URL?) {
        guard let url = url else {
            return
        }
        if self.avPlayer != nil {
            self.avPlayer?.replaceCurrentItem(with: nil)
        }
        self.avPlayer = AVPlayer(url: url)
        // Create AVPlayerLayer from AVPlayer
        let playerLayer = AVPlayerLayer(player: avPlayer)
        // Set frame and other properties if you wish here for your playerLayer
        playerLayer.frame = self.playerView.frame

        // Also remove any attached player first, if exist
        self.playerView.player = nil

        self.playerView.layer.addSublayer(playerLayer)
    }

    func handleNewAirPlaySource() {
        print("CapacitorIVSPlayer AirPlay is active")
        self.airplayButton.removeFromSuperview() // try to hide the airplay selector
        self.playerView.player?.pause()
        createAvPlayer(url: self.player.path!)
        avPlayer?.play()
        // set PLAYING after 1 sec
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            self.playerDelegate.eventEmitter.sendEvent(withName: "onState", body: ["state": "PLAYING"])
        }
        // send to listner
        isCastActive = true
        self.playerDelegate.eventEmitter.sendEvent(withName: "onCastStatus", body: ["isActive": true])
    }

    func removeAvPlayer() {
        // Pause the AVPlayer
        self.avPlayer?.pause()
        self.avPlayer?.rate = 0.0
        print("CapacitorIVSPlayer removeAvPlayer")
        // Detach AVPlayer from AVPlayerLayer
        if let sublayers = self.playerView.layer.sublayers {
            for layer in sublayers {
                if let playerLayer = layer as? AVPlayerLayer {
                    playerLayer.player = nil
                }
            }
        }

        // Remove the AVPlayerLayer
        if let sublayers = self.playerView.layer.sublayers {
            for layer in sublayers {
                if layer is AVPlayerLayer {
                    layer.removeFromSuperlayer()
                }
            }
        }

        // Clear the AVPlayer
        self.avPlayer?.replaceCurrentItem(with: nil)
    }

    func handleAirPlaySourceDeactivated() {
        print("CapacitorIVSPlayer AirPlay is disabled")
        removeAvPlayer()
        isCastActive = false
        self.playerDelegate.eventEmitter.sendEvent(withName: "onCastStatus", body: ["isActive": false])
        // Re-attach the original player to the playerView
        if isClosed {
            return
        }
        self.playerView.player = self.player
        self.player.play()
        self.playerDelegate.eventEmitter.sendEvent(withName: "onState", body: ["state": "PLAYING"])

    }

    @objc func handleAudioRouteChange(_ notification: NSNotification) {
        guard let userInfo = notification.userInfo,
              let reasonValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let _ = AVAudioSession.RouteChangeReason(rawValue: reasonValue) else {
            return
        }
        let session = AVAudioSession.sharedInstance()
        print("CapacitorIVSPlayer handleAudioRouteChange \(reasonValue) \(userInfo)")
        for output in session.currentRoute.outputs {
            print("CapacitorIVSPlayer output \(output.portType)")
            if output.portType == AVAudioSession.Port.airPlay && !isCastActive {
                handleNewAirPlaySource()
            } else if output.portType == AVAudioSession.Port.builtInSpeaker && isCastActive {
                handleAirPlaySourceDeactivated()
            }
        }
    }

    @objc func deviceWillLock() {
        print("CapacitorIVSPlayer deviceWillLock")
        if self.backgroundState != "PLAYING" {
            DispatchQueue.main.async {
                self.player.pause()
            }
        }
    }
    
    @objc func applicationDidEnterBackground(_ notification: NSNotification) {
        print("CapacitorIVSPlayer applicationDidEnterBackground")
        guard #available(iOS 15, *), let pipController = pipController else {
            print("CapacitorIVSPlayer !pipController")
            playerView.player?.pause()
            return
        }
        print("CapacitorIVSPlayer isPictureInPicturePossible: \(pipController.isPictureInPicturePossible)")
        print("CapacitorIVSPlayer isPictureInPictureSuspended: \(pipController.isPictureInPictureSuspended)")
        print("CapacitorIVSPlayer isPictureInPictureActive: \(pipController.isPictureInPictureActive)")
        if !pipController.isPictureInPictureActive {
            playerView.player?.pause()
        }
    }

    @objc func applicationWillEnterForeground(notification: Notification) {
        print("CapacitorIVSPlayer applicationWillEnterForeground")
        lastForegroundEvent = Date();
    }

    @objc func applicationDidBecomeActive(notification: Notification) {
        guard #available(iOS 15, *), let pipController = pipController else {
            return
        }
        print("CapacitorIVSPlayer applicationDidBecomeActive \(pipController.isPictureInPictureActive)")
        if pipController.isPictureInPictureActive && Date().timeIntervalSince(lastForegroundEvent) < 1 {
            pipController.stopPictureInPicture()
            self.playerDelegate.eventEmitter.sendEvent(withName: "stopPip", body: [:])
        }
    }

    @available(iOS 15, *)
    private var pipController: AVPictureInPictureController? {
        get {
            return _pipController as! AVPictureInPictureController?
        }
        set {
            _pipController = newValue
        }
    }

    func fetchImage(from url: URL, completion: @escaping (UIImage?) -> Void) {
        let task = URLSession.shared.dataTask(with: url) { (data, _, error) in
            guard let data = data, let image = UIImage(data: data), error == nil else {
                completion(nil)
                return
            }
            completion(image)
        }
        task.resume()
    }

    func setupNowPlayingInfo(title: String, subTitle: String, url: String) {
        var nowPlayingInfo: [String: Any] = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyArtist: subTitle,
            MPMediaItemPropertyMediaType: MPMediaType.anyVideo.rawValue,
            MPNowPlayingInfoPropertyIsLiveStream: true
        ]
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
        if let imageUrl = URL(string: url) {
            fetchImage(from: imageUrl) { fetchedImage in
                guard let image = fetchedImage else { return }

                let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in return image }
                nowPlayingInfo.updateValue(artwork, forKey: MPMediaItemPropertyArtwork)
                MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
            }
        }
    }

    func setupRemoteTransportControls() {
        let commandCenter = MPRemoteCommandCenter.shared()

        commandCenter.playCommand.isEnabled = true
        commandCenter.playCommand.addTarget { [unowned self] _ in
            if self.player.state != .playing {
                self.player.play()
                return .success
            }
            return .commandFailed
        }

        commandCenter.pauseCommand.isEnabled = true
        commandCenter.pauseCommand.addTarget { [unowned self] _ in
            if self.player.state == .playing {
                self.player.pause()
                return .success
            }
            return .commandFailed
        }
    }

    @objc func getPluginVersion(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        resolve(["version": self.PLUGIN_VERSION])
    }

    @objc func getAutoQuality(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {

        resolve(["autoQuality": self.player.autoQualityMode])
    }

    @objc func setAutoQuality(_ autoQuality: Bool, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        DispatchQueue.main.async {
            self.player.autoQualityMode = autoQuality ?? !self.player.autoQualityMode
        }
        resolve(true)
    }

    @objc func getQualities(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        var qualities = [String]()
        for quality in self.player.qualities {
            qualities.append(quality.name)
        }
        resolve(["qualities": qualities])
    }

    @objc func getQuality(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        resolve(["quality": self.player.quality?.name ?? ""])
    }

    @objc func setQuality(_ quality: String, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        let targetQualityName = quality

        var selectedQuality: IVSQuality?

        // find quality in list
        for quality in self.player.qualities {
            if quality.name == targetQualityName {
                selectedQuality = quality
                break
            }
        }

        // Check if we found quality
        guard let targetQuality = selectedQuality else {
            print("CapacitorIVSPlayer Error: Quality not found")
            reject("failed", "Quality not found", nil)
            return
        }

        // Set quality
        DispatchQueue.main.async {
            self.player.quality = targetQuality
        }

        resolve(true)
    }

    @objc func getFrame(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        let frame = playerView.frame
        let frameDict: [String: CGFloat] = [
            "x": frame.origin.x,
            "y": frame.origin.y,
            "width": frame.size.width,
            "height": frame.size.height
        ]
        resolve(frameDict)
    }

    @objc func getMute(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        print("CapacitorIVSPlayer getMute")
        resolve(["mute": self.player.muted])
    }

    @objc func setMute(_ mute: Bool, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        print("CapacitorIVSPlayer setMute")
        DispatchQueue.main.async {
            if self.isCastActive && (self.avPlayer != nil) {
                self.avPlayer?.isMuted = mute ?? !self.avPlayer!.isMuted
            } else {
                self.player.muted = mute ?? !self.avPlayer!.isMuted
            }
        }
        resolve(true)
    }

    @objc func _setPip(_ pip: Bool) -> Bool {
        print("CapacitorIVSPlayer setPip")
        guard #available(iOS 15, *), let pipController = pipController else {
            return false
        }
        // check if isPictureInPicturePossible
        if !pipController.isPictureInPicturePossible {
            return false
        }
        print("CapacitorIVSPlayer isCastActive \(isCastActive)")
        if isCastActive {
            return false
        }
        let ispip = pip ?? false;
        if ispip {
            isClosed = true
            pipController.startPictureInPicture()
            self.playerDelegate.eventEmitter.sendEvent(withName: "startPip", body: [:])
        } else {
            isClosed = false
            pipController.stopPictureInPicture()
            self.playerDelegate.eventEmitter.sendEvent(withName: "stopPip", body: [:])
        }
        print("CapacitorIVSPlayer _setPip \(ispip) done")
        return true
    }

    @objc func setPip(_ pip: Bool, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        if _setPip(pip) {
            resolve(true)
        } else {
            reject("failed", "Not possible right now", nil)
        }
    }

    @objc func getPip(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        print("CapacitorIVSPlayer getPip")
        guard #available(iOS 15, *), let pipController = pipController else {
            reject("failed", "Not possible right now", nil)
            return
        }
        resolve(["pip": pipController.isPictureInPictureActive])
    }

    func _setFrame(_x: Int? = nil,
                         _y: Int? = nil,
                         w: Int? = nil,
                         h: Int? = nil) -> Bool {
        print("CapacitorIVSPlayer _setFrame")
        let viewController =  self.playerView
        let screenSize: CGRect = UIScreen.main.bounds
        let topPadding = viewController.safeAreaInsets.top

        let x = _x ?? 0
        let y = _y ?? Int(topPadding)
        let width = w ?? Int(round(Float(screenSize.width)))
        let height = h ?? Int(round(Float(screenSize.width * (9.0 / 16.0))))
        self.playerView.playerLayer.zPosition = -1
        self.playerView.frame = CGRect(
            x: x,
            y: y,
            width: width,
            height: height
        )
        print("CapacitorIVSPlayer _setFrame x:\(x) y:\(y) width:\(width) height:\(height) done")
        return true
    }

    @objc func setFrame(_
                      options: NSDictionary,
                      resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        print("CapacitorIVSPlayer setFrame", options)
        let x = Int(round(options["x"] as? Float ?? 0))
        let y = Int(round(options["y"] as? Float ?? 0))
        let width = Int(round(options["width"] as? Float ?? 0))
        let height = Int(round(options["height"] as? Float ?? 0))
        
        print("CapacitorIVSPlayer setFrame x:\(x) y:\(y) width:\(width) height:\(height) done")

        DispatchQueue.main.async {
            if self._setFrame(_x: x, _y: y, w: width, h: height) {
                resolve(true)
            } else {
                print("CapacitorIVSPlayer setFrame failed", options)
                reject("failed", "Unable to _setFrame", nil)
            }
        }
    }

    @objc func _setPlayerPosition(toBack: Bool) -> Bool {
        self.toBack = toBack
        if toBack {
            self.viewController?.view?.backgroundColor = UIColor.clear
            self.viewController?.view?.isOpaque = false
//            self.viewController?.view?.scrollView.backgroundColor = UIColor.clear
//            self.viewController?.view?.scrollView.isOpaque = false
        } else {
            guard let viewController = self.viewController else {
                return false
            }
            viewController.view.bringSubviewToFront(self.playerView)
        }
        print("CapacitorIVSPlayer _setPlayerPosition done")
        return true
    }

    @objc func setPlayerPosition(_ toBack: Bool, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        print("CapacitorIVSPlayer setPlayerPosition")
        DispatchQueue.main.async {
            if self._setPlayerPosition(toBack: toBack) {
                resolve(true)
            } else {
                reject("failed", "Unable to _setPlayerPosition", nil)
            }
        }
    }

    @objc func getPlayerPosition(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        resolve(["toBack": self.toBack])
    }

    @objc func _setBackgroundState(backgroundState: String) -> Bool {
        if ["PAUSED", "PLAYING"].contains(backgroundState)  {
            self.backgroundState = backgroundState
        } else {
            return false
        }
        print("CapacitorIVSPlayer _setBackgroundState done")
        return true
    }

    @objc func setBackgroundState(_ backgroundState: IVSPlayer.State,resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        print("CapacitorIVSPlayer setBackgroundState")
        let backgroundState = stateToStateName(backgroundState) ?? "PAUSED"
        DispatchQueue.main.async {
            if self._setBackgroundState(backgroundState: backgroundState)  {
                resolve(true)
            } else {
                reject("failed", "Invalid backgroundState: \(backgroundState)", nil)
            }
        }
    }

    @objc func getBackgroundState(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        resolve(["backgroundState": self.backgroundState])
    }

    public func loadUrl(url: String) {
        let u = URL(string: url)
        self.player.load(u)
        if self.isCastActive {
            self.createAvPlayer(url: u)
            self.avPlayer?.play()
        }
        print("CapacitorIVSPlayer loadUrl")
    }

    public func cyclePlayer(prevUrl: String, nextUrl: String) -> Bool {
        self.removeAvPlayer()
        if prevUrl != nextUrl {
            // add again after 30 ms
            self.player.pause()
            self.player.load(nil)
            self.playerView.removeFromSuperview()
        }
        self.loadUrl(url: nextUrl)
        return true
    }

    @objc func cast(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        print("CapacitorIVSPlayer cast")

        DispatchQueue.main.async {
            if !self.isCastActive {
                // Create AVPlayer if needed and start playing
                if self.avPlayer != nil {
                    self.avPlayer?.replaceCurrentItem(with: nil)
                }
                self.avPlayer = AVPlayer(url: self.player.path!)
            }

            // Add a AVRoutePickerView to show airplay dialog. You can create this button and add it to your desired place in UI
            self.airplayButton = AVRoutePickerView(frame: CGRect(x: 0, y: 0, width: 30.0, height: 30.0))
            self.airplayButton.activeTintColor = UIColor.blue
            self.airplayButton.tintColor = UIColor.white
            self.viewController?.view.addSubview(self.airplayButton)
//            self.bridge?.parent.viewController?.view.addSubview(self.airplayButton) // Assumes bridge.viewController is the view you want to add to

            // Pressing the button programmatically to show airplay modal
            for subview in self.airplayButton.subviews {
                if let button = subview as? UIButton {
                    button.sendActions(for: .touchUpInside)
                    self.airplayButton.isHidden = true
                    break
                }
            }
        }
        resolve(true)
    }

    @objc func getCastStatus(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        print("CapacitorIVSPlayer getCastStatus")
        resolve(["isActive": isCastActive])
    }

    @objc func create(_ 
                      options: NSDictionary,
                      resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
         print("CapacitorIVSPlayer create", options)                
        let playbackRate = options["playbackRate"] as? Float ?? 1.0
        
        if (self.player.playbackRate != playbackRate && playbackRate >= 0.5 && playbackRate <= 2.0) {
            self.player.playbackRate = playbackRate
        } else {
            self.player.playbackRate = 1.0
        }

        let url = options["url"] as? String ?? ""
        autoPlay = options["autoPlay"] as? Bool ?? false
        toBack = options["toBack"] as? Bool ?? false
        
        DispatchQueue.main.async {
            let title = options["title"] as? String ?? ""
            let subTitle = options["subTitle"] as? String ?? ""
            let cover = options["cover"] as? String ?? ""
            self.setupNowPlayingInfo(title: title, subTitle: subTitle, url: cover)
            self.setupRemoteTransportControls()
            let setupDone = self.cyclePlayer(prevUrl: self.player.path?.absoluteString ?? "", nextUrl: url)
            print("CapacitorIVSPlayer setupDone \(setupDone)")
            self._setPip(false)
            let FrameDone = self._setFrame()
            let PlayerPositionDone = self._setPlayerPosition(toBack: self.toBack)
            if setupDone && FrameDone && PlayerPositionDone {
                self.isClosed = false
                print("CapacitorIVSPlayer success create")
                resolve(true)
            } else {
                reject("failed", "Unable to cyclePlayer \(setupDone) or _setFrame \(FrameDone) or _setPlayerPosition \(PlayerPositionDone)", nil)
            }
        }
    }

    public func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void) {
        print("CapacitorIVSPlayer restoreUserInterfaceForPictureInPictureStopWithCompletionHandler")
        // The user tapped the "restore" button in PiP mode, set the flag to true
        // But first we need to fire the expandPip event so the frontend can prepare the UI
        self.playerDelegate.eventEmitter.sendEvent(withName: "expandPip", body: [:])
        self.didRestorePiP = true
        self.isClosed = false
        completionHandler(true)
    }

    public func pictureInPictureControllerDidStopPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        print("CapacitorIVSPlayer didRestorePiP \(self.didRestorePiP)")
        if self.didRestorePiP {
            // This was a restore from PiP
            self.didRestorePiP = false
            print("CapacitorIVSPlayer expandPip done")
        } else {
            // This was a close PiP
            self.playerDelegate.eventEmitter.sendEvent(withName: "closePip", body: [:])
            print("CapacitorIVSPlayer closePip done")
        }
    }

    private func preparePictureInPicture() {

        guard #available(iOS 15, *), AVPictureInPictureController.isPictureInPictureSupported() else {
            return
        }

        if let existingController = self.pipController {
            if existingController.ivsPlayerLayer == playerView.playerLayer {
                return
            }
            self.pipController = nil
        }

        guard let pipController = AVPictureInPictureController(ivsPlayerLayer: playerView.playerLayer) else {
            return
        }

        self.pipController = pipController
        pipController.delegate = self
        pipController.canStartPictureInPictureAutomaticallyFromInline = true
        print("CapacitorIVSPlayer preparePictureInPicture done")
    }

    @objc func getUrl(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        guard let url = player.path else {
            reject("failed", "No url found", nil)
            return
        }
        resolve(["url": url.absoluteString])
    }

    @objc func getState(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        let stateName = stateToStateName(player.state)
        resolve(["state": stateName])
    }

    @objc func pause(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        print("CapacitorIVSPlayer pause")
        DispatchQueue.main.async {
            if self.isCastActive && (self.avPlayer != nil) {
                self.avPlayer?.pause()
            } else {
                self.player.pause()
            }
        }
        resolve(true)
    }

    @objc func start(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        print("CapacitorIVSPlayer start")
        DispatchQueue.main.async {
            if self.isCastActive && (self.avPlayer != nil) {
                self.avPlayer?.play()
            } else {
                self.player.play()
            }
        }
        resolve(true)
    }

    @objc func delete(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        print("CapacitorIVSPlayer delete")
        DispatchQueue.main.async {
            if self.isCastActive && (self.avPlayer != nil) {
                self.avPlayer?.pause()
            } else {
                self.player.pause()
                self.player.load(nil)
            }
        }
        resolve(true)
    }

    @objc func getSeekPosition(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        var position: Double = 0.0

        if self.isCastActive {
            guard let avPlayer = self.avPlayer else {
                reject("failed", "Player not instantiated", nil)
                return
            }
            
            position = avPlayer.currentTime().seconds
        } else {
            position = self.player.position.seconds
        }

        resolve(["position": position])
    }

    @objc func seekTo(_ position: Double, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        let givenPos = Int64(position ?? -1.0)
        
        if givenPos != -1 {
            let parsedPos = CMTimeMake(value: givenPos, timescale: 1)

            if self.isCastActive {
                guard let avPlayer = self.avPlayer else {
                    reject("failed", "Player not instantiated", nil)
                    return
                }
                
                avPlayer.seek(to: parsedPos)
            } else {
                self.player.seek(to: parsedPos)
            }

            resolve(true)
        } else {
            reject("failed", "Invalid seek position", nil)
        }
    }

    @objc func setPlaybackRate(_ playbackRate: Float, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let givenRate: Float = playbackRate ?? 1.0
        
        if (givenRate < 0.5 || givenRate > 2.0) {
            reject("failed", "Playback rate should be a value between 0.5 and 2.0 (both inclusive), where 1.0 is the default rate.", nil)
        }
        
        DispatchQueue.main.async {
            if self.isCastActive && (self.avPlayer != nil) {
                reject("failed", "Playback rate can not be adjusted while casting!", nil)
            } else {
                self.player.playbackRate = givenRate
                resolve(true)
            }
        }
    }

    @objc func getPlaybackRate(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        DispatchQueue.main.async {
            if self.isCastActive && (self.avPlayer != nil) {
                reject("failed", "Playback rate can not be queried nor adjusted while casting!", nil)
            } else {
                resolve(["playbackRate": self.player.playbackRate])
            }
        }
    }
    
    func applyLastSeekPosition () {
        if self.lastSeekPosBeforeSrcChange == nil {
            return
        }
        
        if self.isCastActive && (self.avPlayer != nil) {
            self.avPlayer!.seek(to: self.lastSeekPosBeforeSrcChange!)
        } else {
            self.player.seek(to: self.lastSeekPosBeforeSrcChange!)
        }
        
        self.lastSeekPosBeforeSrcChange = nil
    }
    
    @objc func updatePlayerSrcUrl(_ url: String, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        let callSrc = url
        
        if callSrc == "" {
            reject("failed", "Source property is required", nil)
            return
        }
        
        if self.isCastActive && (self.avPlayer != nil) {
            self.lastSeekPosBeforeSrcChange = self.avPlayer?.currentTime()
            let prevUrl: String = self.player.path?.absoluteString ?? ""
            
            let done = self.cyclePlayer(prevUrl: prevUrl, nextUrl: callSrc)
            
            if !done {
                reject("failed", "Something went wrong while updating active casting src url", nil)
            }
        } else {
            self.lastSeekPosBeforeSrcChange = self.player.position

            self.loadUrl(url: callSrc)
        }

        resolve(true)
    }
}
