module.exports = {
  extends: [
    'stylelint-config-recommended-scss',
    'stylelint-config-recommended-vue',
    'stylelint-config-recess-order',
  ],
  rules: {
    'at-rule-no-unknown': null,
    'function-no-unknown': null,
    'media-query-no-invalid': null,
    'no-descending-specificity': null,
    'property-no-unknown': null,
    'scss/no-global-function-names': null,
    'selector-class-pattern': null,
    'selector-pseudo-class-no-unknown': null,
    // vue template 内联 style 在部分解析器/IDE 下会误报，关闭该规则避免误报阻塞
    'no-invalid-position-declaration': null,
  },
  ignoreFiles: ['dist/**/*', 'index.html'],
}


