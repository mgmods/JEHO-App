import { bulkApi } from '@/api'
import { toast } from '@/composables/useToast'

/**
 * Run a dashboard bulk action and show a summary toast.
 * @returns {Promise<{ok:number, failed:number, results?: any}|null>}
 */
export async function runBulk({
  resource,
  action,
  ids,
  reason,
  note,
  force,
  t,
}) {
  const list = [...new Set((ids || []).map(String).filter(Boolean))]
  if (!list.length) {
    toast().danger(t ? t('bulk.selectAll') : 'Select items first')
    return null
  }
  const { data, error } = await bulkApi.run({
    resource,
    action,
    ids: list,
    reason,
    note,
    force,
  })
  if (error) {
    toast().danger(error.message)
    return null
  }
  const ok = Number(data?.ok || 0)
  const failed = Number(data?.failed || 0)
  const msg = t
    ? t('bulk.done', { ok, failed })
    : `Done: ${ok} ok · ${failed} failed`
  if (failed > 0) toast().danger(msg)
  else toast().success(msg)
  return data
}
