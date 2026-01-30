import curdGenerator from './plop-template/curd/prompt.mjs'

export default (plop) => {
  // 添加一个名为 "toLowerCase" 的helper
  plop.setHelper('toLowerCase', (text) => {
    return text.toLowerCase()
  })
  plop.setGenerator('curd', curdGenerator)
}
