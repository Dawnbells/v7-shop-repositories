import request from '/@/utils/request'

export function getCountries() {
  return request({
    url: '/address/countries',
    method: 'get',
  })
}

export function pageByCountry(countryCode: string, data: any) {
  return request({
    url: `/address/pageByCountry/${countryCode}`,
    method: 'post',
    data,
  })
}

export function remoteAreaPage(countryCode: string, data: any) {
  return request({
    url: `/address/remoteAreaPage/${countryCode}`,
    method: 'post',
    data,
  })
}