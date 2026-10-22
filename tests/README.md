# Web TV regression checks

These checks use synthetic fixtures and mock network calls. They need no dependencies or TMDB credential.

Run the data and Samsung adapter checks with Node.js 18 or newer:

```sh
node tests/web-recommendations.cjs
node tests/web-samsung-apps.cjs
```

For the real DOM, keyboard handler and CSS, generate an isolated browser test page:

```sh
python3 tests/preview-carousel.py
python3 -m http.server 8765 --bind 127.0.0.1 --directory out/carousel-test
```

Open `http://127.0.0.1:8765/` and use its three test buttons:

- **Run carousel regression test** checks boundaries, reuse of at most five card nodes, global focus, navigation between the shelf and cards, the selected streaming provider, tab filtering, selection after a catalogue reorder, and recovery from an empty catalogue. It also checks that horizontal movement performs no synchronous layout reads or metadata requests.
- **Test continuous movement** dispatches repeated arrow events over five seconds and reports browser animation frame intervals. This measures the current browser, not a TV's frame rate.
- **Test key repeat queue** sends a burst of 200 arrow events, checks that only one additional step is pending, and checks that moving down cancels the pending step. Run it after the preceding test has finished.

The preview uses its own local origin's `fmtv.*` localStorage entries and overwrites those entries with sample data on reload. It does not read `config.properties`, include a real key, or contact the TV. Stop the local server with Ctrl+C.

For hardware validation, build and sign using your own local configuration and existing certificate, then check the following on the TV:

1. From the shelf, press Up, then tap and hold Left/Right. New titles should slide into view with a bounded position counter.
2. Check both ends of the list, all three tabs, Down to return to the shelf, and OK to open the selected provider.
3. On Samsung models using `org.tizen.primevideo` or `org.tizen.globoplayapp`, check that those installed apps appear and launch.
4. With no TMDB key, check the explanatory empty state. With a valid key and available streaming services, check that recommendations appear.

The optimized carousel was installed on a Samsung UN43TU7000GXZD running Tizen 5.5. Its owner confirmed fluid scrolling with the remote. Other TV engines still need hardware coverage.
