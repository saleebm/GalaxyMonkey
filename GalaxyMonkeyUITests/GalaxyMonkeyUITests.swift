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
}
