import XCTest

final class GalaxyMonkeyUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testTitleScreenAppears() throws {
        let app = XCUIApplication()
        app.launch()

        let tapToStart = app.otherElements["Tap to start"]
        XCTAssertTrue(tapToStart.waitForExistence(timeout: 5))

        XCTAssertTrue(app.otherElements["GALAXY MONKEY"].exists)
        XCTAssertTrue(app.otherElements["Left stick to move · Right stick to aim and fire"].exists)
    }

    func testStartTriggersGameAndShowsGameOver() throws {
        let app = XCUIApplication()
        app.launch()

        let tapToStart = app.otherElements["Tap to start"]
        XCTAssertTrue(tapToStart.waitForExistence(timeout: 5))
        tapToStart.tap()

        // Drive game-over via the #if DEBUG accessibility hook installed by
        // GameScene. Mirrors PenguinSlide's debug-force-game-over pattern.
        app.otherElements["debugForceGameOver"].tap()

        let tapAgain = app.otherElements["Tap to play again"]
        XCTAssertTrue(tapAgain.waitForExistence(timeout: 2))
    }

    func testRestartReturnsToPlayableState() throws {
        let app = XCUIApplication()
        app.launch()

        app.otherElements["Tap to start"].tap()
        app.otherElements["debugForceGameOver"].tap()

        let tapAgain = app.otherElements["Tap to play again"]
        XCTAssertTrue(tapAgain.waitForExistence(timeout: 2))
        tapAgain.tap()

        XCTAssertFalse(app.otherElements["Tap to play again"].exists)
    }

    /// Walks the pause-menu → settings → hidden Exploration Mode row. The
    /// row is rendered in SpriteKit, so we hit-test by accessibility label
    /// like the rest of the settings panel. On entry the SwiftUI overlay
    /// takes over; we verify by waiting for the "Back" button injected by
    /// ExplorationView.
    func testExplorationModeEntryPath() throws {
        let app = XCUIApplication()
        app.launch()

        let tapToStart = app.otherElements["Tap to start"]
        XCTAssertTrue(tapToStart.waitForExistence(timeout: 5))
        tapToStart.tap()

        let pauseBtn = app.otherElements["pauseButton"]
        XCTAssertTrue(pauseBtn.waitForExistence(timeout: 3))
        pauseBtn.tap()

        let settingsRow = app.otherElements["Settings"]
        XCTAssertTrue(settingsRow.waitForExistence(timeout: 2))
        settingsRow.tap()

        let enterRow = app.otherElements["Enter exploration mode"]
        XCTAssertTrue(enterRow.waitForExistence(timeout: 2))
        enterRow.tap()

        // ExplorationView's SwiftUI Back button — confirms the RealityView
        // is up and we're really in exploration mode, not still in the
        // settings panel.
        let backButton = app.buttons["Back"]
        XCTAssertTrue(backButton.waitForExistence(timeout: 4))

        // Pause briefly so an external screenshot tool can capture the
        // 3D world. Attach an XCTest screenshot too for the xcresult bundle.
        Thread.sleep(forTimeInterval: 2.0)
        let shot = XCUIScreen.main.screenshot()
        let attach = XCTAttachment(screenshot: shot)
        attach.name = "exploration-mode-active"
        attach.lifetime = .keepAlways
        add(attach)

        backButton.tap()

        // Returning lands us back in the (still paused) game world. The
        // pause button should re-appear and be tappable.
        XCTAssertTrue(pauseBtn.waitForExistence(timeout: 3))
    }

    func testPauseFromInGameShowsMenu() throws {
        let app = XCUIApplication()
        app.launch()

        app.otherElements["Tap to start"].tap()
        app.otherElements["debugForcePause"].tap()

        XCTAssertTrue(app.otherElements["PAUSED"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.otherElements["Resume"].exists)
        XCTAssertTrue(app.otherElements["Settings"].exists)
        XCTAssertTrue(app.otherElements["Quit to Title"].exists)
    }

    func testPauseResumeReturnsToGameplay() throws {
        let app = XCUIApplication()
        app.launch()

        app.otherElements["Tap to start"].tap()
        app.otherElements["debugForcePause"].tap()

        let resume = app.otherElements["Resume"]
        XCTAssertTrue(resume.waitForExistence(timeout: 2))
        resume.tap()

        XCTAssertFalse(app.otherElements["Resume"].exists)
        XCTAssertFalse(app.otherElements["PAUSED"].exists)
    }

    func testSettingsOpensAndBackReturnsToPause() throws {
        let app = XCUIApplication()
        app.launch()

        app.otherElements["Tap to start"].tap()
        app.otherElements["debugForcePause"].tap()

        let settingsRow = app.otherElements["Settings"]
        XCTAssertTrue(settingsRow.waitForExistence(timeout: 2))
        settingsRow.tap()

        XCTAssertTrue(app.otherElements["SETTINGS"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.otherElements["On"].exists)
        XCTAssertTrue(app.otherElements["Off"].exists)
        XCTAssertTrue(app.otherElements["Left"].exists)
        XCTAssertTrue(app.otherElements["Right"].exists)

        let back = app.otherElements["Back"]
        XCTAssertTrue(back.exists)
        back.tap()

        XCTAssertTrue(app.otherElements["Resume"].waitForExistence(timeout: 2))
    }

    func testQuitFromPauseReturnsToTitle() throws {
        let app = XCUIApplication()
        app.launch()

        app.otherElements["Tap to start"].tap()
        app.otherElements["debugForcePause"].tap()

        let quit = app.otherElements["Quit to Title"]
        XCTAssertTrue(quit.waitForExistence(timeout: 2))
        quit.tap()

        XCTAssertTrue(app.otherElements["Tap to start"].waitForExistence(timeout: 2))
    }
}
