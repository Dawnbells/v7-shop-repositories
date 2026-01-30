const notEmpty = (name) => {
  return (v) => {
    if (!v || v.trim === '') return `${name}为必填项`
    else return true
  }
}

export default {
  description: '创建curd',
  prompts: [
    {
      type: 'input',
      name: 'path',
      message: '请输入View路径',
      validate: notEmpty('path'),
    },
    {
      type: 'input',
      name: 'name',
      message: '请输入view名称，然后点击回车',
      validate: notEmpty('name'),
    },
  ],
  actions: () => {
    ///const pathCaseName = '{{ pathCase name }}'
    const properCaseName = '{{ properCase name }}'
    const camelCaseName = '{{ camelCase name }}'
    const path = '{{ pathCase path }}'

    return [
      {
        type: 'add',
        path: `src/views/${path}/${properCaseName}.vue`,
        templateFile: './plop-template/curd/index.hbs',
      },
      {
        type: 'add',
        path: `src/views/${path}/vabAutoComponents/${properCaseName}Edit.vue`,
        templateFile: './plop-template/curd/edit.hbs',
      },
      {
        type: 'add',
        path: `mock/controller/${camelCaseName}.ts`,
        templateFile: './plop-template/curd/mock.hbs',
      },
      {
        type: 'add',
        path: `src/api/${camelCaseName}.ts`,
        templateFile: './plop-template/curd/api.hbs',
      },
    ]
  },
}
