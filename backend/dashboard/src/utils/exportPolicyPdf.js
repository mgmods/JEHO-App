/**
 * Real PDF file download (Arabic RTL) via pdfmake + Amiri font.
 * Does NOT use the browser print dialog.
 */
import pdfMake from 'pdfmake/build/pdfmake'
import pdfFonts from 'pdfmake/build/vfs_fonts'
import { ArabicShaper } from 'arabic-persian-reshaper'
import amiriUrl from '@/assets/fonts/Amiri-Regular.ttf?url'

let fontsReady = null

function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer)
  const chunk = 0x8000
  let binary = ''
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk))
  }
  return btoa(binary)
}

async function ensureFonts() {
  if (fontsReady) return fontsReady
  fontsReady = (async () => {
    const res = await fetch(amiriUrl)
    if (!res.ok) throw new Error('تعذر تحميل خط العربية للـ PDF')
    const amiriB64 = arrayBufferToBase64(await res.arrayBuffer())
    const baseVfs = pdfFonts?.pdfMake?.vfs || pdfFonts || {}
    pdfMake.addVirtualFileSystem({
      ...baseVfs,
      'Amiri-Regular.ttf': amiriB64,
    })
    pdfMake.addFonts({
      Amiri: {
        normal: 'Amiri-Regular.ttf',
        bold: 'Amiri-Regular.ttf',
        italics: 'Amiri-Regular.ttf',
        bolditalics: 'Amiri-Regular.ttf',
      },
    })
  })()
  return fontsReady
}

/** Shape Arabic so glyphs connect correctly in PDF. */
function ar(value) {
  const s = String(value ?? '')
  if (!s) return ''
  if (!/[\u0600-\u06FF]/.test(s)) return s
  try {
    return ArabicShaper.convertArabic(s)
  } catch {
    return s
  }
}

function fmtNum(n) {
  const x = Number(n)
  if (!Number.isFinite(x)) return '—'
  return x.toLocaleString('en-US')
}

function pct(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  return n <= 1 ? `${Math.round(n * 100)}%` : `${Math.round(n)}%`
}

function cell(text, opts = {}) {
  return {
    text: ar(text),
    alignment: opts.align || 'center',
    fontSize: opts.fontSize || 9,
    bold: !!opts.bold,
    color: opts.color || '#142018',
    margin: opts.margin || [0, 2, 0, 2],
  }
}

function heading(title) {
  return {
    text: ar(title),
    style: 'h1',
    margin: [0, 16, 0, 10],
  }
}

function para(text) {
  return {
    text: ar(text),
    style: 'body',
    margin: [0, 0, 0, 8],
  }
}

function bullets(items) {
  return {
    ul: (items || []).filter(Boolean).map((t) => ({
      text: ar(t),
      fontSize: 13,
      margin: [0, 2, 0, 2],
    })),
    style: 'body',
    margin: [0, 0, 0, 10],
  }
}

function table(headers, rows) {
  const body = [
    headers.map((h) => ({
      text: ar(h),
      bold: true,
      color: '#ffffff',
      fillColor: '#1fa86a',
      alignment: 'center',
      fontSize: 11,
      margin: [3, 6, 3, 6],
    })),
    ...rows.map((row, idx) =>
      row.map((c) => ({
        text: ar(c),
        alignment: 'center',
        fontSize: 11,
        fillColor: idx % 2 === 0 ? '#ffffff' : '#f3faf6',
        margin: [3, 5, 3, 5],
      })),
    ),
  ]
  return {
    table: {
      headerRows: 1,
      widths: headers.map(() => '*'),
      body,
    },
    layout: {
      hLineWidth: () => 0.6,
      vLineWidth: () => 0.6,
      hLineColor: () => '#cbbd9d',
      vLineColor: () => '#cbbd9d',
    },
    margin: [0, 6, 0, 12],
  }
}

function linkButton(label, url) {
  return {
    text: ar(label),
    link: url,
    color: '#ffffff',
    fillColor: '#1fa86a',
    alignment: 'center',
    margin: [0, 10, 0, 8],
    fontSize: 14,
    bold: true,
  }
}

function coverBlock(title, lines) {
  return [
    {
      text: ar(title),
      style: 'h2',
      margin: [0, 10, 0, 6],
    },
    bullets(lines),
  ]
}

function buildDocDefinition(payload) {
  const {
    meta = {},
    enabled = {},
    economy = {},
    paymentFlags = {},
    promo = null,
    editable = {},
    hostTargetRows = [],
    activeSections = [],
    logoDataUrl = null,
    generatedAt = '',
  } = payload || {}

  const content = []

  // ── Page 1: App introduction (full cover) ──
  if (logoDataUrl) {
    content.push({
      image: logoDataUrl,
      width: 96,
      alignment: 'center',
      margin: [0, 8, 0, 12],
    })
  }
  content.push({ text: 'JEHO CHAT', style: 'brand', alignment: 'center' })
  content.push({
    text: ar(meta.title || 'دليل سياسة المنصة'),
    style: 'coverTitle',
    alignment: 'center',
  })
  content.push({
    text: ar(meta.audience || 'للمضيفات · فتح الوكالات · الداعمين'),
    style: 'audience',
    alignment: 'center',
  })

  content.push(
    para(
      meta.intro ||
        'JEHO CHAT تطبيق غرف صوتية مباشرة: تلتقي بالمضيفات، ترسل الهدايا، تفتح وكالة، وتكسب من التارجت والنشاط داخل الروم.',
    ),
  )

  content.push(
    ...coverBlock('ما هو تطبيق JEHO CHAT؟', [
      'منصة غرف صوتية حية للدردشة والدعم والترفيه.',
      'المضيف يدير الروم ويستقبل الهدايا والدعوات.',
      'الداعم يشحن العملات ويرسل هدايا ويتفاعل مع المضيفات.',
      'الوكيل يفتح وكالة ويدير المضيفات ويحصل على حصته من النشاط.',
    ]),
  )

  content.push(
    ...coverBlock('لمن هذا الملف؟', [
      'للمضيفات: فهم التارجت الشهري وجدول الرواتب وكيف يُحسب الألماس.',
      'لفتّاحي الوكالات: نسب التقسيم، شروط الفتح، وراتب الوكيل.',
      'للداعمين: باقات الشحن، الهدايا، VIP، وصناديق الحظ.',
    ]),
  )

  content.push(
    ...coverBlock('ماذا ستجد داخل الدليل؟', [
      'اقتصاد العملات والألماس وقواعد السحب.',
      'تقسيم الهدايا بين المنصة والوكالة والمضيف.',
      'كتالوج الهدايا وباقات الشحن والعروض.',
      'جدول تارجت المضيف (٣٤ مرحلة) مع راتب المضيف والوكيل.',
      'العضوية VIP والآي دي المميز ومتجر المظهر.',
      'رابط تحميل التطبيق الرسمي من Google Play.',
    ]),
  )

  if (meta.coverExtra) {
    content.push(para(meta.coverExtra))
  }

  if (generatedAt) {
    content.push({
      text: ar(`تاريخ الإصدار: ${generatedAt}`),
      style: 'muted',
      alignment: 'center',
      margin: [0, 8, 0, 4],
    })
  }

  if (meta.appDownloadUrl) {
    content.push(linkButton(meta.appDownloadLabel || 'تحميل تطبيق JEHO CHAT من Google Play', meta.appDownloadUrl))
    content.push({
      text: meta.appDownloadUrl,
      link: meta.appDownloadUrl,
      color: '#0f7a4d',
      alignment: 'center',
      fontSize: 11,
      margin: [0, 0, 0, 8],
    })
  }

  content.push({ text: '', pageBreak: 'after' })

  if (enabled.toc) {
    content.push(heading('فهرس المحتوى'))
    content.push(
      bullets((activeSections || []).map((s, i) => `${i + 1}. ${s.label || s.id}`)),
    )
  }

  if (enabled.economy) {
    content.push(heading('١) الاقتصاد والعملات'))
    content.push(
      para(
        'العملات تُشترى بالشحن وتُنفق على الهدايا والألعاب والعضوية والمول. الألماس يُسكّ من الهدايا للمستلم ويمكن سحبه نقداً وفق الحد الأدنى.',
      ),
    )
    content.push(
      bullets([
        `سكّ هدية عادية → ألماس بنسبة ${pct(economy.giftDiamondRatio)} من قيمة العملات`,
        `سكّ هدية حظ → ألماس بنسبة ${pct(economy.luckyGiftDiamondRatio)}`,
        `تبديل ألماس → عملات: ×${economy.diamondCoinRate ?? '—'}`,
        `سعر السحب: ١ ألماسة ≈ $${economy.diamondUsdRate ?? '—'}`,
        `حد أدنى للسحب: ${fmtNum(economy.withdrawTargetDiamonds)} ألماسة`,
        'ممنوع الدعم الذاتي',
      ]),
    )
    if (editable.withdrawPackages?.length) {
      content.push(
        table(
          ['باقة سحب', 'ألماس', 'قيمة تقريبية $'],
          editable.withdrawPackages.map((w, i) => [
            String(i + 1),
            fmtNum(w.diamonds),
            `$${w.usd ?? '—'}`,
          ]),
        ),
      )
    }
  }

  if (enabled.split) {
    content.push(heading('٢) تقسيم الهدايا والوكالة'))
    content.push(
      table(
        ['الحالة', 'المنصة', 'الوكالة', 'المضيف'],
        [
          [
            'روم وكالة',
            pct(economy.platformShare),
            pct(economy.agencyShare),
            pct(economy.hostShareWithAgency),
          ],
          [
            'بلا وكالة',
            pct(economy.platformShare),
            '—',
            pct(economy.hostShareSolo),
          ],
        ],
      ),
    )
    content.push(
      bullets([
        `فتح وكالة ≈ ${fmtNum(economy.agencyCreateCoins)} عملة + موافقة الإدارة`,
        'لا يوجد نظام عائلات — الوكالة هي النظام الرسمي',
        `دعوة ضيف جديد: ${economy.hostInviteDiamonds ?? '—'} ألماسة · حد ${economy.hostInviteMaxPerDay ?? '—'}/يوم`,
        'روم خدمة العملاء: غرفة رسمية مجانية تضعها الإدارة أعلى القائمة',
      ]),
    )
  }

  if (enabled.gifts && editable.gifts?.length) {
    content.push(heading('٣) كتالوج الهدايا'))
    content.push(
      table(
        ['الهدية', 'النوع', 'السعر'],
        editable.gifts.map((g) => [g.name, g.typeLabel || g.type || '—', fmtNum(g.coinPrice)]),
      ),
    )
  }

  if (enabled.luckyTiers && editable.luckyGiftTiers?.length) {
    content.push(heading('٤) هدايا الحظ'))
    content.push(para('للداعم: قد يرجع مردود عملات. للمضيف: سكّ ألماس بنسبة أقل من العادي.'))
    content.push(
      table(
        ['الطبقة', 'السعر', 'سكّ للمستلم'],
        editable.luckyGiftTiers.map((t) => [
          t.name,
          `${fmtNum(t.coinPrice)} عملة`,
          pct(economy.luckyGiftDiamondRatio),
        ]),
      ),
    )
  }

  if (enabled.packages && editable.packages?.length) {
    content.push(heading('٥) باقات الشحن'))
    content.push(
      table(
        ['السعر', 'العملات', 'بونص', 'الإجمالي'],
        editable.packages.map((p) => [
          `$${p.priceUsd}`,
          fmtNum(p.coins),
          fmtNum(p.bonusCoins || 0),
          fmtNum((p.coins || 0) + (p.bonusCoins || 0)),
        ]),
      ),
    )
  }

  if (enabled.offers) {
    content.push(heading('٦) عروض المتجر'))
    if (editable.storeOffers?.length) {
      content.push(
        table(
          ['العرض', 'السعر', 'عملات', 'بونص'],
          editable.storeOffers.map((o) => [
            o.title,
            `$${o.priceUsd}`,
            fmtNum(o.coins),
            fmtNum(o.bonusCoins || 0),
          ]),
        ),
      )
    } else {
      content.push(para('لا عروض مفعّلة حالياً'))
    }
  }

  if (enabled.promos && promo) {
    content.push(heading('٧) العروض والترقيات'))
    if (promo.monthlyOffers?.length) {
      content.push({ text: ar('عروض شهرية'), style: 'h2' })
      content.push(
        table(
          ['العرض', 'العتبة $', 'المكافأة'],
          promo.monthlyOffers.map((o) => [
            o.titleAr || o.titleEn,
            `$${o.thresholdUsd}`,
            `${o.rewardDays} يوم`,
          ]),
        ),
      )
    }
    if (promo.agentTiers?.length) {
      content.push({ text: ar('بونص وكلاء الشحن'), style: 'h2' })
      content.push(
        table(
          ['المستوى', 'شحن $', 'بونص'],
          promo.agentTiers.map((t) => [
            t.titleAr || t.id,
            `$${t.thresholdUsd}`,
            `+${t.bonusPercent}%`,
          ]),
        ),
      )
    }
    if (promo.supporterPacks?.length) {
      content.push({ text: ar('باقات الداعم'), style: 'h2' })
      content.push(
        table(
          ['الباقة', 'العتبة $', 'المدة'],
          promo.supporterPacks.map((p) => [
            p.titleAr || p.titleEn,
            `$${p.thresholdUsd}`,
            `${p.rewardDays} يوم`,
          ]),
        ),
      )
    }
  }

  if (enabled.vip && editable.vipPlans?.length) {
    content.push(heading('٨) العضوية المميزة VIP'))
    content.push(
      table(
        ['المستوى', 'الاسم', 'السعر / شهر'],
        editable.vipPlans.map((v) => [String(v.level), v.name, fmtNum(v.priceCoins)]),
      ),
    )
  }

  if (enabled.vanity && editable.vanityIds?.length) {
    content.push(heading('٩) الآي دي المميز'))
    content.push(
      table(
        ['الآي دي', 'السعر'],
        editable.vanityIds.map((v) => [v.code || v.id || v.vanityId, fmtNum(v.priceCoins || v.price)]),
      ),
    )
  }

  if (enabled.cosmetics && editable.cosmetics?.length) {
    content.push(heading('١٠) الإطارات والدخولية'))
    content.push(
      table(
        ['الاسم', 'النوع', 'السعر'],
        editable.cosmetics.map((c) => [
          c.name,
          c.typeLabel || c.type || '—',
          fmtNum(c.priceCoins || c.price),
        ]),
      ),
    )
  }

  if (enabled.lucky && editable.luckyBoxes?.length) {
    content.push(heading('١١) صناديق الحظ'))
    content.push(
      table(
        ['الصندوق', 'السعر', 'ملاحظة'],
        editable.luckyBoxes.map((b) => [
          b.name,
          b.priceCoins ? `${fmtNum(b.priceCoins)} عملة` : 'مجاني',
          b.note || '—',
        ]),
      ),
    )
  }

  if (enabled.hostTarget) {
    content.push(heading('١٢) تارجت المضيفة وجدول الرواتب'))
    content.push(
      para('التارجت بالكوينز، وقاعدة العرض: ١ كوين = ١ ألماسة. عند إكمال المرحلة: راتب المضيف + راتب الوكيل بالدولار.'),
    )
    if (hostTargetRows?.length) {
      // RTL visual order: rightmost column first in pdfmake row array when alignment is center — keep logical LTR headers matching Arabic table right-to-left reading by reversing for display
      content.push(
        table(
          ['الإجمالي', 'راتب الوكيل', 'راتب المضيف', '١ كوين = ١ ألماسة', 'التارجت (كوينز)', '#'],
          hostTargetRows.map((r) => [
            `$${r.totalUsd}`,
            `$${r.agentSalaryUsd}`,
            `$${r.hostSalaryUsd}`,
            fmtNum(r.threshold),
            fmtNum(r.threshold),
            String(r.stage),
          ]),
        ),
      )
    } else {
      content.push(para('لا يوجد جدول تارجت — حمّله من صفحة تارجت المضيف ثم حدّث.'))
    }
    if (meta.appDownloadUrl) {
      content.push(linkButton(meta.appDownloadLabel || 'تحميل تطبيق JEHO CHAT', meta.appDownloadUrl))
    }
  }

  if (enabled.tasks) {
    content.push(heading('١٣) المهام والدعوات'))
    content.push(
      bullets([
        'مركز المهام يومياً — نقاط/فضة/عملات حسب المهمة',
        `دعوة روم مكتملة: ${economy.hostInviteDiamonds ?? '—'} ألماسة (حد ${economy.hostInviteMaxPerDay ?? '—'}/يوم)`,
        'الألماس الأساسي من الهدايا والتارجت والدعوات',
      ]),
    )
  }

  if (enabled.engage) {
    content.push(heading('١٤) مسابقات · ترتيب · كأس · ألعاب · دراما · بنرات'))
    content.push(
      bullets([
        'مسابقات موسمية ولوحات ترتيب (ثروة/سحر) يوم/أسبوع/شهر',
        'كأس الروم: تنافس الغرف على نشاط الهدايا',
        'الدراما: حلقات قصيرة مع مكافآت مشاهدة عند التفعيل',
        'البنرات تظهر في الصفحة الرئيسية للتطبيق',
      ]),
    )
    if (editable.games?.length) {
      content.push(table(['الألعاب المتاحة'], editable.games.map((g) => [g.name])))
    }
  }

  if (enabled.payments) {
    content.push(heading('١٥) الشحن والدفع ووكلاء الشحن'))
    const payItems = ['Google Play / متجر التطبيق للشحن الرسمي']
    if (paymentFlags.shamCash) payItems.push('ShamCash متاح كقناة محلية')
    if (paymentFlags.binancePay) payItems.push('Binance Pay عند التفعيل')
    if (paymentFlags.fourthwall) payItems.push('قنوات متجر إضافية عند التفعيل')
    payItems.push('وكلاء الشحن المعتمدون داخل التطبيق فقط')
    content.push(bullets(payItems))
    content.push(para('لا تشارك بيانات الدفع خارج القنوات الرسمية في JEHO CHAT.'))
  }

  if (enabled.rules) {
    content.push(heading('١٦) قواعد عامة'))
    content.push(
      bullets([
        'الاحتيال والحسابات المتعددة = إيقاف المكافآت أو الحظر',
        'التفسير النهائي لإدارة JEHO CHAT',
      ]),
    )
  }

  content.push({
    text: ar('JEHO CHAT · ملف سياسة المنصة'),
    style: 'muted',
    alignment: 'center',
    margin: [0, 20, 0, 0],
  })

  return {
    pageSize: 'A4',
    pageMargins: [28, 32, 28, 32],
    defaultStyle: {
      font: 'Amiri',
      fontSize: 13,
      alignment: 'right',
      lineHeight: 1.35,
    },
    styles: {
      brand: { fontSize: 15, bold: true, color: '#8a6a12', margin: [0, 0, 0, 6] },
      coverTitle: { fontSize: 24, bold: true, color: '#102016', margin: [0, 6, 0, 8] },
      audience: { fontSize: 14, color: '#0f7a4d', margin: [0, 0, 0, 12] },
      h1: { fontSize: 17, bold: true, color: '#102016' },
      h2: { fontSize: 14, bold: true, color: '#0f7a4d', margin: [0, 8, 0, 4] },
      body: { fontSize: 13, color: '#1b2420', alignment: 'right', lineHeight: 1.4 },
      muted: { fontSize: 11, color: '#5b655f' },
    },
    content,
    info: {
      title: meta.title || 'JEHO CHAT Policy',
      author: 'JEHO CHAT',
      subject: 'Platform policy brochure',
    },
  }
}

async function logoToDataUrl(src) {
  if (!src) return null
  if (String(src).startsWith('data:')) return src
  try {
    const res = await fetch(src, { credentials: 'same-origin' })
    if (!res.ok) return null
    const blob = await res.blob()
    return await new Promise((resolve) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result || '') || null)
      reader.onerror = () => resolve(null)
      reader.readAsDataURL(blob)
    })
  } catch {
    return null
  }
}

function triggerBlobDownload(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename.endsWith('.pdf') ? filename : `${filename}.pdf`
  a.rel = 'noopener'
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  setTimeout(() => {
    URL.revokeObjectURL(url)
    a.remove()
  }, 1500)
}

/**
 * @param {object} payload structured brochure data from PolicyBrochureView
 * @param {string} filename e.g. JEHO-CHAT-policy-2026-08-03.pdf
 */
export async function exportPolicyPdf(payload, filename = 'JEHO-CHAT-policy.pdf') {
  if (!payload || typeof payload !== 'object') {
    throw new Error('لا توجد بيانات للتصدير')
  }

  await ensureFonts()

  const logoDataUrl = await logoToDataUrl(payload.logoSrc)
  const docDefinition = buildDocDefinition({
    ...payload,
    logoDataUrl,
  })

  const blob = await new Promise((resolve, reject) => {
    try {
      pdfMake.createPdf(docDefinition).getBlob((b) => {
        if (!b) reject(new Error('فشل إنشاء ملف PDF'))
        else resolve(b)
      })
    } catch (e) {
      reject(e instanceof Error ? e : new Error(String(e)))
    }
  })

  triggerBlobDownload(blob, filename)
  return blob.size
}

// keep unused cell helper referenced for tree-shaking clarity in some bundlers
void cell
