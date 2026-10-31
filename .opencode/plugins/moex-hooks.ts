import { Plugin } from "@opencode/plugin"

// AGENTS.md: перед запуском Gradle нужен JDK 25; системный JAVA_HOME указывает на JDK 17.
// Путь к JDK 25 берётся только из переменной MOEX_JAVA25_HOME; если её нет, JAVA_HOME не меняется.
const JAVA_25_HOME = process.env.MOEX_JAVA25_HOME

export default Plugin.define({
  id: "moex.hooks",
  async setup(ctx) {
    await ctx.shell.hook("create.before", (event) => {
      if (JAVA_25_HOME && /(^|[\s/\\])gradlew(\.bat)?\b/.test(event.command)) {
        event.env.JAVA_HOME = JAVA_25_HOME
      }
    })
  },
})
