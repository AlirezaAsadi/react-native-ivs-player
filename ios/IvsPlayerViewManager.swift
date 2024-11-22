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

@objc(PluginArgs)
class PluginArgs: NSObject {
    private var options: NSDictionary
    
    init(options: NSDictionary) {
        self.options = options
    }
    
    // Generic method to retrieve a value for a given key with a default value
    func get<T>(_ key: String, _ defaultValue: T) -> T {
        if let value = options[key] as? T {
            return value
        }
        return defaultValue
    }
    
    // Convenience methods for specific types
    func getBool<T>(_ key: String, _ defaultValue: T) -> T {
        return get(key, defaultValue)
    }
    
    func getString(_ key: String, _ defaultValue: String) -> String {
        return get(key, defaultValue)
    }
    
    func getInt(_ key: String, _ defaultValue: Int?) -> Int? {
        return get(key, defaultValue)
    }
    
    func getFloat(_ key: String, _ defaultValue: Float = 0.0) -> Float {
        return get(key, defaultValue)
    }
    
    // Method to check if a key exists
    func hasOption(_ key: String) -> Bool {
        return options[key] != nil
    }
}

class ReactNativeIVSPlayer: NSObject, IVSPlayer.Delegate {
    
    var plugin: IvsPlayerViewManager!
    
    func player(_ player: IVSPlayer, didChangeState state: IVSPlayer.State) {
        //        print("ReactNativeIVSPlayer state change \(state)")
        let stateName = stateToStateName(state)
        print("ReactNativeIVSPlayer \(stateName)")
        if state == .ready && plugin.autoPlay &&
            !plugin.isCastActive {
            plugin.player.play()
        }
        // when playing add to view
        if state == .playing {
            plugin.viewController.view.addSubview(plugin.playerView)
            plugin.viewController.view.clipsToBounds = true
            plugin.playerView.frame = plugin.viewController.view.frame
            plugin.playerView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            plugin.playerView.videoGravity = .resizeAspect
            
            plugin.applyLastSeekPosition()
        }
        plugin.notifyListeners("onState", data: ["state": stateName])
    }
    
    func player(_ player: IVSPlayer, didOutputCue cue: IVSCue) {
        plugin.notifyListeners("onCue", data: ["cue": cue])
    }
    
    func player(_ player: IVSPlayer, didChangeDuration duration: CMTime) {
        plugin.notifyListeners("onDuration", data: ["duration": duration.seconds])
    }
    
    func player(_ player: IVSPlayer, didFailWithError error: Error) {
        plugin.notifyListeners("onError", data: ["error": error.localizedDescription])
    }
    
    func playerWillRebuffer(_ player: IVSPlayer) {
        plugin.notifyListeners("onRebuffer", data: [:])
    }
    func player(_ player: IVSPlayer, didSeekTo time: CMTime) {
        plugin.notifyListeners("onSeekCompleted", data: ["position": time.seconds])
    }
    func player(_ player: IVSPlayer, didChangeVideoSize videoSize: CGSize) {
        plugin.notifyListeners("onVideoSize", data: ["videoSize": videoSize])
    }
    
    func player(_ player: IVSPlayer, didChangeQuality quality: IVSQuality?) {
        plugin.notifyListeners("onQuality", data: ["quality": quality?.name ?? ""])
    }
    
}

class PlayerBaseView: IVSPlayerView {
    private var currentScale: CGFloat = 1.0
    private var initialCenter: CGPoint = .zero
    private var lastPinchLocation: CGPoint = .zero
    
    override init(frame: CGRect) {
        super.init(frame: frame)
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
    }
    
    public func resetZoomAndPosition() {
        // Reset the scale (identity transform means no scaling or translation)
        self.transform = CGAffineTransform.identity
        
        // Reset the position to the initial center
        self.center = CGPoint(x: self.bounds.width / 2, y: self.bounds.height / 2)
        
        // Reset the current scale and initial center for future gestures
        self.currentScale = 1.0
        self.initialCenter = self.center
        self.lastPinchLocation = self.center
    }
    
    
    public func setupZoomGestures() {
        self.resetZoomAndPosition()
        // Pinch Gesture for both Zooming and Panning
        let pinchGesture = UIPinchGestureRecognizer(target: self, action: #selector(handlePinch(_:)))
        pinchGesture.delegate = self
        self.addGestureRecognizer(pinchGesture)
    }
    
    @objc private func handlePinch(_ sender: UIPinchGestureRecognizer) {
        guard let view = sender.view else { return }
        
        switch sender.state {
        case .began:
            // Store the initial center and pinch location
            initialCenter = view.center
            lastPinchLocation = sender.location(in: view.superview)
            
        case .changed:
            // Handle zoom (scaling) with a max limit of 4.0
            let newScale = min(currentScale * sender.scale, 4.0)  // Limit the scale to a maximum of 4
            
            if newScale >= 1.0 {
                view.transform = CGAffineTransform(scaleX: newScale, y: newScale)
                currentScale = newScale
            } else {
                view.transform = CGAffineTransform.identity
                currentScale = 1.0
            }
            sender.scale = 1.0 // Reset scale for next pinch
            
            // Handle translation (moving)
            let currentPinchLocation = sender.location(in: view.superview)
            let translation = CGPoint(
                x: currentPinchLocation.x - lastPinchLocation.x,
                y: currentPinchLocation.y - lastPinchLocation.y
            )
            var newCenter = CGPoint(
                x: initialCenter.x + translation.x,
                y: initialCenter.y + translation.y
            )
            
            // Adjust the center to make sure the view stays within bounds
            newCenter = adjustCenterForBounds(newCenter)
            view.center = newCenter
            
        case .ended, .cancelled:
            // Adjust the view position after zoom and translation
            adjustViewPositionAfterZoom(view: view)
            initialCenter = view.center
            
        default:
            break
        }
    }
    
    private func adjustViewPositionAfterZoom(view: UIView) {
        let scaledWidth = bounds.width * currentScale
        let scaledHeight = bounds.height * currentScale
        
        var newCenter = view.center
        
        // Constrain the new center within the allowed bounds
        let offsetX = max(0, (scaledWidth - bounds.width) / 2)
        let offsetY = max(0, (scaledHeight - bounds.height) / 2)
        
        newCenter.x = max(bounds.width / 2 - offsetX, min(bounds.width / 2 + offsetX, newCenter.x))
        newCenter.y = max(bounds.height / 2 - offsetY, min(bounds.height / 2 + offsetY, newCenter.y))
        
        view.center = newCenter
    }
    
    private func adjustCenterForBounds(_ center: CGPoint) -> CGPoint {
        let scaledWidth = bounds.width * currentScale
        let scaledHeight = bounds.height * currentScale
        
        var adjustedCenter = center
        
        // Constrain the new center within the allowed bounds
        let offsetX = max(0, (scaledWidth - bounds.width) / 2)
        let offsetY = max(0, (scaledHeight - bounds.height) / 2)
        
        adjustedCenter.x = max(bounds.width / 2 - offsetX, min(bounds.width / 2 + offsetX, adjustedCenter.x))
        adjustedCenter.y = max(bounds.height / 2 - offsetY, min(bounds.height / 2 + offsetY, adjustedCenter.y))
        
        return adjustedCenter
    }
    
}

extension PlayerBaseView: UIGestureRecognizerDelegate {
    public func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        return true // Allow pinch and pan to be recognized simultaneously
    }
}

@objc(IvsPlayerViewManager)
public class IvsPlayerViewManager: RCTViewManager, AVPictureInPictureControllerDelegate {
    
    private let PLUGIN_VERSION = "0.13.34"
    
    let event = EventEmitter()
    let player = IVSPlayer()
    let playerDelegate = ReactNativeIVSPlayer()
    let playerView = PlayerBaseView()
    let viewController: UIViewController = UIViewController()
    private var _pipController: Any?
    private var isFScreen = false
    private var originalFrame: CGRect?
    private var originalParent: UIView?
    private var airplayButton = AVRoutePickerView()
    var didRestorePiP: Bool = false
    var isClosed: Bool = true
    var autoPlay: Bool = false
    var _cover: String = ""
    var _thumbnailUrl: String = ""
    var isCastActive: Bool = false
    var avPlayer: AVPlayer?
    var backgroundState: String = "PAUSED"
    var lastForegroundEvent: Date = Date();
    var lastSeekPosBeforeSrcChange: CMTime? = nil
    
    @objc var url: String? {
        didSet {
            if let nextUrl = url, !url!.isEmpty {
                self.cyclePlayer(prevUrl: self.player.path?.absoluteString ?? "", nextUrl: nextUrl)
            }
        }
    }
    
    public override init() {
        super.init()
        self.viewController.view.backgroundColor = UIColor.black
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("ReactNativeIVSPlayer ‼️ Could not setup AVAudioSession: \(error)")
        }
        playerDelegate.plugin = self
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
    
    public override func view() -> UIView? {
        return self.viewController.view
    }
    
    deinit {
        self._delete()
    }
    
    public func notifyListeners(_ withName: String, data: [String: Any]?) {
        event.sendEvent(withName: withName, body: data)
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
        print("ReactNativeIVSPlayer AirPlay is active")
        guard let playerPathUrl = self.player.path else {
            // Handle the case where player.path is nil, e.g., log an error or return early
            print("player.path is nil")
            return
        }

        self.airplayButton.removeFromSuperview() // try to hide the airplay selector
        self.playerView.player?.pause()
        createAvPlayer(url: playerPathUrl)
        avPlayer?.play()

        // set PLAYING after 1 sec
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            self.notifyListeners("onState", data: ["state": "PLAYING"])
        }

        // send to listener
        isCastActive = true
        self.notifyListeners("onCastStatus", data: ["isActive": true])
    }
    
    func removeAvPlayer() {
        // Pause the AVPlayer
        self.avPlayer?.pause()
        self.avPlayer?.rate = 0.0
        print("ReactNativeIVSPlayer removeAvPlayer")
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
        print("ReactNativeIVSPlayer AirPlay is disabled")
        removeAvPlayer()
        isCastActive = false
        self.notifyListeners("onCastStatus", data: ["isActive": false])
        // Re-attach the original player to the playerView
        if isClosed {
            return
        }
        self.playerView.player = self.player
        self.player.play()
        self.notifyListeners("onState", data: ["state": "PLAYING"])
        
    }
    
    @objc func handleAudioRouteChange(_ notification: NSNotification) {
        guard let userInfo = notification.userInfo,
              let reasonValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let _ = AVAudioSession.RouteChangeReason(rawValue: reasonValue) else {
            return
        }
        let session = AVAudioSession.sharedInstance()
        print("ReactNativeIVSPlayer handleAudioRouteChange \(reasonValue) \(userInfo)")
        for output in session.currentRoute.outputs {
            print("ReactNativeIVSPlayer output \(output.portType)")
            if output.portType == AVAudioSession.Port.airPlay && !isCastActive {
                handleNewAirPlaySource()
            } else if output.portType == AVAudioSession.Port.builtInSpeaker && isCastActive {
                handleAirPlaySourceDeactivated()
            }
        }
    }
    
    @objc func deviceWillLock() {
        print("ReactNativeIVSPlayer deviceWillLock")
        if self.backgroundState != "PLAYING" {
            DispatchQueue.main.async {
                self.player.pause()
            }
        }
    }
    
    @objc func applicationDidEnterBackground(_ notification: NSNotification) {
        print("ReactNativeIVSPlayer applicationDidEnterBackground")
        guard #available(iOS 15, *), let pipController = pipController else {
            print("ReactNativeIVSPlayer !pipController")
            playerView.player?.pause()
            return
        }
        print("ReactNativeIVSPlayer isPictureInPicturePossible: \(pipController.isPictureInPicturePossible)")
        print("ReactNativeIVSPlayer isPictureInPictureSuspended: \(pipController.isPictureInPictureSuspended)")
        print("ReactNativeIVSPlayer isPictureInPictureActive: \(pipController.isPictureInPictureActive)")
        if !pipController.isPictureInPictureActive {
            playerView.player?.pause()
        }
    }
    
    @objc func applicationWillEnterForeground(notification: Notification) {
        print("ReactNativeIVSPlayer applicationWillEnterForeground")
        lastForegroundEvent = Date();
    }
    
    @objc func applicationDidBecomeActive(notification: Notification) {
        guard #available(iOS 15, *), let pipController = pipController else {
            return
        }
        print("ReactNativeIVSPlayer applicationDidBecomeActive \(pipController.isPictureInPictureActive)")
        if pipController.isPictureInPictureActive && Date().timeIntervalSince(lastForegroundEvent) < 1 {
            pipController.stopPictureInPicture()
            self.notifyListeners("stopPip", data: [:])
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
            print("ReactNativeIVSPlayer playCommand triggered, isCastActive: \(isCastActive)")
            if (self.isCastActive && (self.avPlayer != nil)) {
                avPlayer?.play()
            } else {
                self.player.play()
            }
            return .success
        }
        
        commandCenter.pauseCommand.isEnabled = true
        commandCenter.pauseCommand.addTarget { [unowned self] _ in
            print("ReactNativeIVSPlayer pauseCommand triggered, isCastActive: \(isCastActive)")
            if (self.isCastActive && (self.avPlayer != nil)) {
                avPlayer?.pause()
            } else {
                self.player.pause()
            }
            return .success
        }
        
    }
    
    @objc func getPluginVersion(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        resolve(["version": self.PLUGIN_VERSION])
    }

    @objc func resetZoom(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        playerView.resetZoomAndPosition()
        resolve(true)
    }
    
    @objc func getAutoQuality(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        
        resolve(["autoQuality": self.player.autoQualityMode])
    }
    
    @objc func setAutoQuality(_ options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        print("ReactNativeIVSPlayer setAutoQuality...")
        
        let call = PluginArgs(options: options)
        DispatchQueue.main.async {
            self.player.autoQualityMode = call.getBool("autoQuality", !self.player.autoQualityMode) ?? true
        }
        resolve(true)
    }
    
    @objc func getQualities(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        var qualities = [String]()
        for quality in self.player.qualities {
            qualities.append(quality.name)
        }
        resolve(["qualities": qualities])
    }
    
    @objc func getQuality(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        resolve(["quality": self.player.quality?.name ?? ""])
    }
    
    @objc func setQuality(_ options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let call = PluginArgs(options: options)
        let targetQualityName = call.getString("quality", "")
        
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
            print("ReactNativeIVSPlayer Error: Quality not found")
            reject("failed", "Quality not found", nil)
            return
        }
        
        // Set quality
        DispatchQueue.main.async {
            self.player.quality = targetQuality
        }
        
        resolve(true)
    }
    
    @objc func getMute(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        print("ReactNativeIVSPlayer getMute")
        resolve(["mute": self.player.muted])
    }
    
    @objc func setMute(_ options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        print("ReactNativeIVSPlayer setMute")
        let call = PluginArgs(options: options)
        DispatchQueue.main.async {
            if self.isCastActive && (self.avPlayer != nil) {
                self.avPlayer?.isMuted = call.getBool("mute", !self.avPlayer!.isMuted)
            } else {
                self.player.muted = call.getBool("mute", !self.player.muted)
            }
        }
        resolve(true)
    }
    
    @objc func _setPip(_ options: NSDictionary) -> Bool {
        print("ReactNativeIVSPlayer setPip")
        let call = PluginArgs(options: options)
        guard #available(iOS 15, *), let pipController = pipController else {
            print("ReactNativeIVSPlayer pipController is unavailable")
            return false
        }
        // check if isPictureInPicturePossible
        if !pipController.isPictureInPicturePossible {
            print("ReactNativeIVSPlayer isPictureInPicturePossible is false")
            return false
        }
        print("ReactNativeIVSPlayer isCastActive \(isCastActive)")
        if isCastActive {
            return false
        }
        let ispip = call.getBool("pip", false) ?? false
        if ispip {
            isClosed = true
            pipController.startPictureInPicture()
            self.notifyListeners("startPip", data: [:])
        } else {
            isClosed = false
            pipController.stopPictureInPicture()
            self.notifyListeners("stopPip", data: [:])
        }
        print("ReactNativeIVSPlayer _setPip \(ispip) done")
        return true
    }
    public override static func requiresMainQueueSetup() -> Bool {
        return true
    }
    
    @objc func setPip(_ options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        if _setPip(options) {
            resolve(true)
        } else {
            reject("failed", "Not possible right now", nil)
        }
    }
    
    @objc func getPip(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        print("ReactNativeIVSPlayer getPip")
        guard #available(iOS 15, *), let pipController = pipController else {
            reject("failed", "Not possible right now", nil)
            return
        }
        resolve(["pip": pipController.isPictureInPictureActive])
    }
    
    @objc func _setBackgroundState(backgroundState: String) -> Bool {
        if ["PAUSED", "PLAYING"].contains(backgroundState)  {
            self.backgroundState = backgroundState
        } else {
            return false
        }
        print("ReactNativeIVSPlayer _setBackgroundState done")
        return true
    }
    
    @objc func setBackgroundState(_ options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        print("ReactNativeIVSPlayer setBackgroundState")
        let call = PluginArgs(options: options)
        let backgroundState: String = call.getString("backgroundState", "PAUSED")
        DispatchQueue.main.async {
            if self._setBackgroundState(backgroundState: backgroundState)  {
                resolve(true)
            } else {
                reject("failed", "Invalid backgroundState: \(backgroundState)", nil)
            }
        }
    }
    
    @objc func getBackgroundState(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        resolve(["backgroundState": self.backgroundState])
    }
    
    public func loadUrl(url: String) {
        let u = URL(string: url)
        self.player.load(u)
        if self.isCastActive {
            self.createAvPlayer(url: u)
            self.avPlayer?.play()
        }
        print("ReactNativeIVSPlayer loadUrl")
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
    
    @objc func cast(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        print("ReactNativeIVSPlayer cast")
        
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
            self.viewController.view.addSubview(self.airplayButton) // Assumes bridge.viewController is the view you want to add to
            
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
    
    @objc func getCastStatus(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        print("ReactNativeIVSPlayer getCastStatus")
        resolve(["isActive": isCastActive])
    }
    
    @objc func create(_ options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let call = PluginArgs(options: options)
        let playbackRate = call.getFloat("playbackRate", 1.0)
        
        if (self.player.playbackRate != playbackRate && playbackRate >= 0.5 && playbackRate <= 2.0) {
            self.player.playbackRate = playbackRate
        } else {
            self.player.playbackRate = 1.0
        }
        
        let url = call.getString("url", "")
        autoPlay = call.getBool("autoPlay", false)
        DispatchQueue.main.async {
            let title = call.getString("title", "")
            let subTitle = call.getString("subtitle", "")
            let cover = call.getString("cover", "")
            let zoom = call.getBool("zoom", false)
            let _options = options;
            self.setupNowPlayingInfo(title: title, subTitle: subTitle, url: cover)
            self.setupRemoteTransportControls()
            let setupDone = self.cyclePlayer(prevUrl: self.player.path?.absoluteString ?? "", nextUrl: url)
            print("ReactNativeIVSPlayer setupDone \(setupDone)")
            self._setPip(options)
            
            if (zoom) {
                self.playerView.setupZoomGestures()
                print("ReactNativeIVSPlayer zoom setup is done")
            }
            if setupDone {
                self.isClosed = false
                print("ReactNativeIVSPlayer success create")
                resolve(true)
            } else {
                reject("failed", "Unable to cyclePlayer \(setupDone)", nil)
            }
        }
    }
    
    public func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void) {
        print("ReactNativeIVSPlayer restoreUserInterfaceForPictureInPictureStopWithCompletionHandler")
        // The user tapped the "restore" button in PiP mode, set the flag to true
        // But first we need to fire the expandPip event so the frontend can prepare the UI
        self.notifyListeners("expandPip", data: [:])
        self.didRestorePiP = true
        self.isClosed = false
        completionHandler(true)
    }
    
    public func pictureInPictureControllerDidStopPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        print("ReactNativeIVSPlayer didRestorePiP \(self.didRestorePiP)")
        if self.didRestorePiP {
            // This was a restore from PiP
            self.didRestorePiP = false
            print("ReactNativeIVSPlayer expandPip done")
        } else {
            // This was a close PiP
            self.notifyListeners("closePip", data: [:])
            print("ReactNativeIVSPlayer closePip done")
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
        print("ReactNativeIVSPlayer preparePictureInPicture done")
    }
    
    @objc func getUrl(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        guard let url = player.path else {
            reject("failed", "No url found", nil)
            return
        }
        resolve(["url": url.absoluteString])
    }
    
    @objc func getState(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let stateName = stateToStateName(player.state)
        resolve(["state": stateName])
    }
    
    @objc func pause(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        print("ReactNativeIVSPlayer pause")
        DispatchQueue.main.async {
            if self.isCastActive && (self.avPlayer != nil) {
                self.avPlayer?.pause()
            } else {
                self.player.pause()
            }
        }
        resolve(true)
    }
    
    @objc func start(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        print("ReactNativeIVSPlayer start")
        DispatchQueue.main.async {
            if self.isCastActive && (self.avPlayer != nil) {
                self.avPlayer?.play()
            } else {
                self.player.play()
            }
        }
        resolve(true)
    }
    
    @objc func _delete() {
        print("ReactNativeIVSPlayer delete")
        
        // Close pip before deleting
        self._setPip(["pip": false])
        
        // Stop player
        DispatchQueue.main.async {
            if self.isCastActive && (self.avPlayer != nil) {
                self.avPlayer?.pause()
            } else {
                self.player.pause()
                self.player.load(nil)
            }
        }
    }
    
    @objc func delete(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        self._delete()
        resolve(true)
    }
    
    @objc func getSeekPosition(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
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
    
    @objc func seekTo(_ options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let call = PluginArgs(options: options)
        let givenPos = Int64(call.getFloat("position", -1.0))
        
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
    
    @objc func setPlaybackRate(_ options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let call = PluginArgs(options: options)
        let givenRate: Float = call.getFloat("playbackRate") ?? 1.0
        
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
    
    @objc func updatePlayerSrcUrl(_ options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let call = PluginArgs(options: options)
        let callSrc = call.getString("url", "")
        
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
            resolve(true)
        }
        
    }
}
