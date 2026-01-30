export {}

declare global {
  interface NavigatorClipboard {
    readonly clipboard: {
      writeText(newClipText: string): Promise<void>
      readText(): Promise<string>
    }
  }

  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface Navigator extends NavigatorClipboard {}
}
