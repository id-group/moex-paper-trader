import { Plugin } from "@opencode/plugin"

// AGENTS.md: перед запуском Gradle нужен JDK 25; системный JAVA_HOME указывает на JDK 17.
const JAVA_25_HOME = process.env.MOEX_JAVA25_HOME ?? "C:\\Users\\id-gr\\.jdks\\liberica-full-25.0.4"

export default Plugin.define({
  id: "moex.hooks",
  async setup(ctx) {
    await ctx.shell.hook("create.before", (event) => {
      if (/(^|[\s/\\])gradlew(\.bat)?\b/.test(event.command)) {
        event.env.JAVA_HOME = JAVA_25_HOME
      }
    })
  },
})
