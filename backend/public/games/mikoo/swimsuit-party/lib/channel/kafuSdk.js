!function (t) {
    "function" == typeof define && define.amd ? define(t) : t()
}((function () {
    "use strict";

    function t(t, e) {
        for (var n = 0; n < e.length; n++) {
            var r = e[n];
            r.enumerable = r.enumerable || !1, r.configurable = !0, "value" in r && (r.writable = !0), Object.defineProperty(t, (o = r.key, i = void 0, "symbol" == typeof (i = function (t, e) {
                if ("object" != typeof t || null === t) return t;
                var n = t[Symbol.toPrimitive];
                if (void 0 !== n) {
                    var r = n.call(t, e || "default");
                    if ("object" != typeof r) return r;
                    throw new TypeError("@@toPrimitive must return a primitive value.")
                }
                return ("string" === e ? String : Number)(t)
            }(o, "string")) ? i : String(i)), r)
        }
        var o, i
    }

    var e = "undefined" != typeof globalThis ? globalThis : "undefined" != typeof window ? window : "undefined" != typeof global ? global : "undefined" != typeof self ? self : {};

    function n(t) {
        var e = {exports: {}};
        return t(e, e.exports), e.exports
    }

    var r, o, i = function (t) {
            return t && t.Math == Math && t
        },
        a = i("object" == typeof globalThis && globalThis) || i("object" == typeof window && window) || i("object" == typeof self && self) || i("object" == typeof e && e) || function () {
            return this
        }() || Function("return this")(), u = function (t) {
            try {
                return !!t()
            } catch (t) {
                return !0
            }
        }, c = !u((function () {
            return 7 != Object.defineProperty({}, 1, {
                get: function () {
                    return 7
                }
            })[1]
        })), f = !u((function () {
            var t = function () {
            }.bind();
            return "function" != typeof t || t.hasOwnProperty("prototype")
        })), l = Function.prototype.call, s = f ? l.bind(l) : function () {
            return l.apply(l, arguments)
        }, p = {}.propertyIsEnumerable, g = Object.getOwnPropertyDescriptor, v = {
            f: g && !p.call({1: 2}, 1) ? function (t) {
                var e = g(this, t);
                return !!e && e.enumerable
            } : p
        }, y = function (t, e) {
            return {enumerable: !(1 & t), configurable: !(2 & t), writable: !(4 & t), value: e}
        }, d = Function.prototype, h = d.call, b = f && d.bind.bind(h, h), m = f ? b : function (t) {
            return function () {
                return h.apply(t, arguments)
            }
        }, S = m({}.toString), w = m("".slice), O = function (t) {
            return w(S(t), 8, -1)
        }, x = Object, E = m("".split), I = u((function () {
            return !x("z").propertyIsEnumerable(0)
        })) ? function (t) {
            return "String" == O(t) ? E(t, "") : x(t)
        } : x, N = function (t) {
            return null == t
        }, j = TypeError, A = function (t) {
            if (N(t)) throw j("Can't call method on " + t);
            return t
        }, P = function (t) {
            return I(A(t))
        }, _ = "object" == typeof document && document.all, T = {all: _, IS_HTMLDDA: void 0 === _ && void 0 !== _},
        R = T.all, M = T.IS_HTMLDDA ? function (t) {
            return "function" == typeof t || t === R
        } : function (t) {
            return "function" == typeof t
        }, C = T.all, k = T.IS_HTMLDDA ? function (t) {
            return "object" == typeof t ? null !== t : M(t) || t === C
        } : function (t) {
            return "object" == typeof t ? null !== t : M(t)
        }, F = function (t, e) {
            return arguments.length < 2 ? (n = a[t], M(n) ? n : void 0) : a[t] && a[t][e];
            var n
        }, D = m({}.isPrototypeOf), L = "undefined" != typeof navigator && String(navigator.userAgent) || "", G = a.process,
        W = a.Deno, U = G && G.versions || W && W.version, V = U && U.v8;
    V && (o = (r = V.split("."))[0] > 0 && r[0] < 4 ? 1 : +(r[0] + r[1])), !o && L && (!(r = L.match(/Edge\/(\d+)/)) || r[1] >= 74) && (r = L.match(/Chrome\/(\d+)/)) && (o = +r[1]);
    var K = o, z = !!Object.getOwnPropertySymbols && !u((function () {
            var t = Symbol();
            return !String(t) || !(Object(t) instanceof Symbol) || !Symbol.sham && K && K < 41
        })), J = z && !Symbol.sham && "symbol" == typeof Symbol.iterator, B = Object, Y = J ? function (t) {
            return "symbol" == typeof t
        } : function (t) {
            var e = F("Symbol");
            return M(e) && D(e.prototype, B(t))
        }, $ = String, X = TypeError, H = function (t) {
            if (M(t)) return t;
            throw X(function (t) {
                try {
                    return $(t)
                } catch (t) {
                    return "Object"
                }
            }(t) + " is not a function")
        }, Q = function (t, e) {
            var n = t[e];
            return N(n) ? void 0 : H(n)
        }, q = TypeError, Z = Object.defineProperty, tt = function (t, e) {
            try {
                Z(a, t, {value: e, configurable: !0, writable: !0})
            } catch (n) {
                a[t] = e
            }
            return e
        }, et = "__core-js_shared__", nt = a[et] || tt(et, {}), rt = n((function (t) {
            (t.exports = function (t, e) {
                return nt[t] || (nt[t] = void 0 !== e ? e : {})
            })("versions", []).push({
                version: "3.29.1",
                mode: "global",
                copyright: "© 2014-2023 Denis Pushkarev (zloirock.ru)",
                license: "https://github.com/zloirock/core-js/blob/v3.29.1/LICENSE",
                source: "https://github.com/zloirock/core-js"
            })
        })), ot = Object, it = function (t) {
            return ot(A(t))
        }, at = m({}.hasOwnProperty), ut = Object.hasOwn || function (t, e) {
            return at(it(t), e)
        }, ct = 0, ft = Math.random(), lt = m(1..toString), st = function (t) {
            return "Symbol(" + (void 0 === t ? "" : t) + ")_" + lt(++ct + ft, 36)
        }, pt = a.Symbol, gt = rt("wks"), vt = J ? pt.for || pt : pt && pt.withoutSetter || st, yt = function (t) {
            return ut(gt, t) || (gt[t] = z && ut(pt, t) ? pt[t] : vt("Symbol." + t)), gt[t]
        }, dt = TypeError, ht = yt("toPrimitive"), bt = function (t, e) {
            if (!k(t) || Y(t)) return t;
            var n, r = Q(t, ht);
            if (r) {
                if (void 0 === e && (e = "default"), n = s(r, t, e), !k(n) || Y(n)) return n;
                throw dt("Can't convert object to primitive value")
            }
            return void 0 === e && (e = "number"), function (t, e) {
                var n, r;
                if ("string" === e && M(n = t.toString) && !k(r = s(n, t))) return r;
                if (M(n = t.valueOf) && !k(r = s(n, t))) return r;
                if ("string" !== e && M(n = t.toString) && !k(r = s(n, t))) return r;
                throw q("Can't convert object to primitive value")
            }(t, e)
        }, mt = function (t) {
            var e = bt(t, "string");
            return Y(e) ? e : e + ""
        }, St = a.document, wt = k(St) && k(St.createElement), Ot = function (t) {
            return wt ? St.createElement(t) : {}
        }, xt = !c && !u((function () {
            return 7 != Object.defineProperty(Ot("div"), "a", {
                get: function () {
                    return 7
                }
            }).a
        })), Et = Object.getOwnPropertyDescriptor, It = {
            f: c ? Et : function (t, e) {
                if (t = P(t), e = mt(e), xt) try {
                    return Et(t, e)
                } catch (t) {
                }
                if (ut(t, e)) return y(!s(v.f, t, e), t[e])
            }
        }, Nt = c && u((function () {
            return 42 != Object.defineProperty((function () {
            }), "prototype", {value: 42, writable: !1}).prototype
        })), jt = String, At = TypeError, Pt = function (t) {
            if (k(t)) return t;
            throw At(jt(t) + " is not an object")
        }, _t = TypeError, Tt = Object.defineProperty, Rt = Object.getOwnPropertyDescriptor, Mt = "enumerable",
        Ct = "configurable", kt = "writable", Ft = {
            f: c ? Nt ? function (t, e, n) {
                if (Pt(t), e = mt(e), Pt(n), "function" == typeof t && "prototype" === e && "value" in n && kt in n && !n[kt]) {
                    var r = Rt(t, e);
                    r && r[kt] && (t[e] = n.value, n = {
                        configurable: Ct in n ? n[Ct] : r[Ct],
                        enumerable: Mt in n ? n[Mt] : r[Mt],
                        writable: !1
                    })
                }
                return Tt(t, e, n)
            } : Tt : function (t, e, n) {
                if (Pt(t), e = mt(e), Pt(n), xt) try {
                    return Tt(t, e, n)
                } catch (t) {
                }
                if ("get" in n || "set" in n) throw _t("Accessors not supported");
                return "value" in n && (t[e] = n.value), t
            }
        }, Dt = c ? function (t, e, n) {
            return Ft.f(t, e, y(1, n))
        } : function (t, e, n) {
            return t[e] = n, t
        }, Lt = Function.prototype, Gt = c && Object.getOwnPropertyDescriptor, Wt = ut(Lt, "name"), Ut = {
            EXISTS: Wt, PROPER: Wt && "something" === function () {
            }.name, CONFIGURABLE: Wt && (!c || c && Gt(Lt, "name").configurable)
        }, Vt = m(Function.toString);
    M(nt.inspectSource) || (nt.inspectSource = function (t) {
        return Vt(t)
    });
    var Kt, zt, Jt, Bt = nt.inspectSource, Yt = a.WeakMap, $t = M(Yt) && /native code/.test(String(Yt)),
        Xt = rt("keys"), Ht = function (t) {
            return Xt[t] || (Xt[t] = st(t))
        }, Qt = {}, qt = "Object already initialized", Zt = a.TypeError, te = a.WeakMap;
    if ($t || nt.state) {
        var ee = nt.state || (nt.state = new te);
        ee.get = ee.get, ee.has = ee.has, ee.set = ee.set, Kt = function (t, e) {
            if (ee.has(t)) throw Zt(qt);
            return e.facade = t, ee.set(t, e), e
        }, zt = function (t) {
            return ee.get(t) || {}
        }, Jt = function (t) {
            return ee.has(t)
        }
    } else {
        var ne = Ht("state");
        Qt[ne] = !0, Kt = function (t, e) {
            if (ut(t, ne)) throw Zt(qt);
            return e.facade = t, Dt(t, ne, e), e
        }, zt = function (t) {
            return ut(t, ne) ? t[ne] : {}
        }, Jt = function (t) {
            return ut(t, ne)
        }
    }
    var re = {
            set: Kt, get: zt, has: Jt, enforce: function (t) {
                return Jt(t) ? zt(t) : Kt(t, {})
            }, getterFor: function (t) {
                return function (e) {
                    var n;
                    if (!k(e) || (n = zt(e)).type !== t) throw Zt("Incompatible receiver, " + t + " required");
                    return n
                }
            }
        }, oe = n((function (t) {
            var e = Ut.CONFIGURABLE, n = re.enforce, r = re.get, o = String, i = Object.defineProperty, a = m("".slice),
                f = m("".replace), l = m([].join), s = c && !u((function () {
                    return 8 !== i((function () {
                    }), "length", {value: 8}).length
                })), p = String(String).split("String"), g = t.exports = function (t, r, u) {
                    "Symbol(" === a(o(r), 0, 7) && (r = "[" + f(o(r), /^Symbol\(([^)]*)\)/, "$1") + "]"), u && u.getter && (r = "get " + r), u && u.setter && (r = "set " + r), (!ut(t, "name") || e && t.name !== r) && (c ? i(t, "name", {
                        value: r,
                        configurable: !0
                    }) : t.name = r), s && u && ut(u, "arity") && t.length !== u.arity && i(t, "length", {value: u.arity});
                    try {
                        u && ut(u, "constructor") && u.constructor ? c && i(t, "prototype", {writable: !1}) : t.prototype && (t.prototype = void 0)
                    } catch (t) {
                    }
                    var g = n(t);
                    return ut(g, "source") || (g.source = l(p, "string" == typeof r ? r : "")), t
                };
            Function.prototype.toString = g((function () {
                return M(this) && r(this).source || Bt(this)
            }), "toString")
        })), ie = function (t, e, n, r) {
            r || (r = {});
            var o = r.enumerable, i = void 0 !== r.name ? r.name : e;
            if (M(n) && oe(n, i, r), r.global) o ? t[e] = n : tt(e, n); else {
                try {
                    r.unsafe ? t[e] && (o = !0) : delete t[e]
                } catch (t) {
                }
                o ? t[e] = n : Ft.f(t, e, {
                    value: n,
                    enumerable: !1,
                    configurable: !r.nonConfigurable,
                    writable: !r.nonWritable
                })
            }
            return t
        }, ae = Math.ceil, ue = Math.floor, ce = Math.trunc || function (t) {
            var e = +t;
            return (e > 0 ? ue : ae)(e)
        }, fe = function (t) {
            var e = +t;
            return e != e || 0 === e ? 0 : ce(e)
        }, le = Math.max, se = Math.min, pe = function (t, e) {
            var n = fe(t);
            return n < 0 ? le(n + e, 0) : se(n, e)
        }, ge = Math.min, ve = function (t) {
            return (e = t.length) > 0 ? ge(fe(e), 9007199254740991) : 0;
            var e
        }, ye = function (t) {
            return function (e, n, r) {
                var o, i = P(e), a = ve(i), u = pe(r, a);
                if (t && n != n) {
                    for (; a > u;) if ((o = i[u++]) != o) return !0
                } else for (; a > u; u++) if ((t || u in i) && i[u] === n) return t || u || 0;
                return !t && -1
            }
        }, de = {includes: ye(!0), indexOf: ye(!1)}.indexOf, he = m([].push), be = function (t, e) {
            var n, r = P(t), o = 0, i = [];
            for (n in r) !ut(Qt, n) && ut(r, n) && he(i, n);
            for (; e.length > o;) ut(r, n = e[o++]) && (~de(i, n) || he(i, n));
            return i
        },
        me = ["constructor", "hasOwnProperty", "isPrototypeOf", "propertyIsEnumerable", "toLocaleString", "toString", "valueOf"],
        Se = me.concat("length", "prototype"), we = {
            f: Object.getOwnPropertyNames || function (t) {
                return be(t, Se)
            }
        }, Oe = {f: Object.getOwnPropertySymbols}, xe = m([].concat), Ee = F("Reflect", "ownKeys") || function (t) {
            var e = we.f(Pt(t)), n = Oe.f;
            return n ? xe(e, n(t)) : e
        }, Ie = function (t, e, n) {
            for (var r = Ee(e), o = Ft.f, i = It.f, a = 0; a < r.length; a++) {
                var u = r[a];
                ut(t, u) || n && ut(n, u) || o(t, u, i(e, u))
            }
        }, Ne = /#|\.prototype\./, je = function (t, e) {
            var n = Pe[Ae(t)];
            return n == Te || n != _e && (M(e) ? u(e) : !!e)
        }, Ae = je.normalize = function (t) {
            return String(t).replace(Ne, ".").toLowerCase()
        }, Pe = je.data = {}, _e = je.NATIVE = "N", Te = je.POLYFILL = "P", Re = je, Me = It.f, Ce = function (t, e) {
            var n, r, o, i, u, c = t.target, f = t.global, l = t.stat;
            if (n = f ? a : l ? a[c] || tt(c, {}) : (a[c] || {}).prototype) for (r in e) {
                if (i = e[r], o = t.dontCallGetSet ? (u = Me(n, r)) && u.value : n[r], !Re(f ? r : c + (l ? "." : "#") + r, t.forced) && void 0 !== o) {
                    if (typeof i == typeof o) continue;
                    Ie(i, o)
                }
                (t.sham || o && o.sham) && Dt(i, "sham", !0), ie(n, r, i, t)
            }
        }, ke = {};
    ke[yt("toStringTag")] = "z";
    var Fe, De = "[object z]" === String(ke), Le = yt("toStringTag"), Ge = Object, We = "Arguments" == O(function () {
            return arguments
        }()), Ue = De ? O : function (t) {
            var e, n, r;
            return void 0 === t ? "Undefined" : null === t ? "Null" : "string" == typeof (n = function (t, e) {
                try {
                    return t[e]
                } catch (t) {
                }
            }(e = Ge(t), Le)) ? n : We ? O(e) : "Object" == (r = O(e)) && M(e.callee) ? "Arguments" : r
        }, Ve = String, Ke = function (t) {
            if ("Symbol" === Ue(t)) throw TypeError("Cannot convert a Symbol value to a string");
            return Ve(t)
        }, ze = function () {
            var t = Pt(this), e = "";
            return t.hasIndices && (e += "d"), t.global && (e += "g"), t.ignoreCase && (e += "i"), t.multiline && (e += "m"), t.dotAll && (e += "s"), t.unicode && (e += "u"), t.unicodeSets && (e += "v"), t.sticky && (e += "y"), e
        }, Je = a.RegExp, Be = u((function () {
            var t = Je("a", "y");
            return t.lastIndex = 2, null != t.exec("abcd")
        })), Ye = Be || u((function () {
            return !Je("a", "y").sticky
        })), $e = {
            BROKEN_CARET: Be || u((function () {
                var t = Je("^r", "gy");
                return t.lastIndex = 2, null != t.exec("str")
            })), MISSED_STICKY: Ye, UNSUPPORTED_Y: Be
        }, Xe = Object.keys || function (t) {
            return be(t, me)
        }, He = c && !Nt ? Object.defineProperties : function (t, e) {
            Pt(t);
            for (var n, r = P(e), o = Xe(e), i = o.length, a = 0; i > a;) Ft.f(t, n = o[a++], r[n]);
            return t
        }, Qe = {f: He}, qe = F("document", "documentElement"), Ze = "prototype", tn = "script", en = Ht("IE_PROTO"),
        nn = function () {
        }, rn = function (t) {
            return "<" + tn + ">" + t + "</" + tn + ">"
        }, on = function (t) {
            t.write(rn("")), t.close();
            var e = t.parentWindow.Object;
            return t = null, e
        }, an = function () {
            try {
                Fe = new ActiveXObject("htmlfile")
            } catch (t) {
            }
            var t, e, n;
            an = "undefined" != typeof document ? document.domain && Fe ? on(Fe) : (e = Ot("iframe"), n = "java" + tn + ":", e.style.display = "none", qe.appendChild(e), e.src = String(n), (t = e.contentWindow.document).open(), t.write(rn("document.F=Object")), t.close(), t.F) : on(Fe);
            for (var r = me.length; r--;) delete an[Ze][me[r]];
            return an()
        };
    Qt[en] = !0;
    var un, cn, fn = Object.create || function (t, e) {
            var n;
            return null !== t ? (nn[Ze] = Pt(t), n = new nn, nn[Ze] = null, n[en] = t) : n = an(), void 0 === e ? n : Qe.f(n, e)
        }, ln = a.RegExp, sn = u((function () {
            var t = ln(".", "s");
            return !(t.dotAll && t.exec("\n") && "s" === t.flags)
        })), pn = a.RegExp, gn = u((function () {
            var t = pn("(?<a>b)", "g");
            return "b" !== t.exec("b").groups.a || "bc" !== "b".replace(t, "$<a>c")
        })), vn = re.get, yn = rt("native-string-replace", String.prototype.replace), dn = RegExp.prototype.exec, hn = dn,
        bn = m("".charAt), mn = m("".indexOf), Sn = m("".replace), wn = m("".slice),
        On = (cn = /b*/g, s(dn, un = /a/, "a"), s(dn, cn, "a"), 0 !== un.lastIndex || 0 !== cn.lastIndex),
        xn = $e.BROKEN_CARET, En = void 0 !== /()??/.exec("")[1];
    (On || En || xn || sn || gn) && (hn = function (t) {
        var e, n, r, o, i, a, u, c = this, f = vn(c), l = Ke(t), p = f.raw;
        if (p) return p.lastIndex = c.lastIndex, e = s(hn, p, l), c.lastIndex = p.lastIndex, e;
        var g = f.groups, v = xn && c.sticky, y = s(ze, c), d = c.source, h = 0, b = l;
        if (v && (y = Sn(y, "y", ""), -1 === mn(y, "g") && (y += "g"), b = wn(l, c.lastIndex), c.lastIndex > 0 && (!c.multiline || c.multiline && "\n" !== bn(l, c.lastIndex - 1)) && (d = "(?: " + d + ")", b = " " + b, h++), n = new RegExp("^(?:" + d + ")", y)), En && (n = new RegExp("^" + d + "$(?!\\s)", y)), On && (r = c.lastIndex), o = s(dn, v ? n : c, b), v ? o ? (o.input = wn(o.input, h), o[0] = wn(o[0], h), o.index = c.lastIndex, c.lastIndex += o[0].length) : c.lastIndex = 0 : On && o && (c.lastIndex = c.global ? o.index + o[0].length : r), En && o && o.length > 1 && s(yn, o[0], n, (function () {
            for (i = 1; i < arguments.length - 2; i++) void 0 === arguments[i] && (o[i] = void 0)
        })), o && g) for (o.groups = a = fn(null), i = 0; i < g.length; i++) a[(u = g[i])[0]] = o[u[1]];
        return o
    });
    var In = hn;
    Ce({target: "RegExp", proto: !0, forced: /./.exec !== In}, {exec: In});
    var Nn = function (t) {
        if ("Function" === O(t)) return m(t)
    }, jn = yt("species"), An = RegExp.prototype, Pn = Object.is || function (t, e) {
        return t === e ? 0 !== t || 1 / t == 1 / e : t != t && e != e
    }, _n = TypeError;
    !function (t, e, n, r) {
        var o = yt(t), i = !u((function () {
            var e = {};
            return e[o] = function () {
                return 7
            }, 7 != ""[t](e)
        })), a = i && !u((function () {
            var e = !1, n = /a/;
            return "split" === t && ((n = {}).constructor = {}, n.constructor[jn] = function () {
                return n
            }, n.flags = "", n[o] = /./[o]), n.exec = function () {
                return e = !0, null
            }, n[o](""), !e
        }));
        if (!i || !a || n) {
            var c = Nn(/./[o]), f = e(o, ""[t], (function (t, e, n, r, o) {
                var a = Nn(t), u = e.exec;
                return u === In || u === An.exec ? i && !o ? {done: !0, value: c(e, n, r)} : {
                    done: !0,
                    value: a(n, e, r)
                } : {done: !1}
            }));
            ie(String.prototype, t, f[0]), ie(An, o, f[1])
        }
        r && Dt(An[o], "sham", !0)
    }("search", (function (t, e, n) {
        return [function (e) {
            var n = A(this), r = N(e) ? void 0 : Q(e, t);
            return r ? s(r, e, n) : new RegExp(e)[t](Ke(n))
        }, function (t) {
            var r = Pt(this), o = Ke(t), i = n(e, r, o);
            if (i.done) return i.value;
            var a = r.lastIndex;
            Pn(a, 0) || (r.lastIndex = 0);
            var u = function (t, e) {
                var n = t.exec;
                if (M(n)) {
                    var r = s(n, t, e);
                    return null !== r && Pt(r), r
                }
                if ("RegExp" === O(t)) return s(In, t, e);
                throw _n("RegExp#exec called on incompatible receiver")
            }(r, o);
            return Pn(r.lastIndex, a) || (r.lastIndex = a), null === u ? -1 : u.index
        }]
    }));
    var Tn = a, Rn = String, Mn = TypeError, Cn = Object.setPrototypeOf || ("__proto__" in {} ? function () {
            var t, e = !1, n = {};
            try {
                (t = function (t, e, n) {
                    try {
                        return m(H(Object.getOwnPropertyDescriptor(t, e)[n]))
                    } catch (t) {
                    }
                }(Object.prototype, "__proto__", "set"))(n, []), e = n instanceof Array
            } catch (t) {
            }
            return function (n, r) {
                return Pt(n), function (t) {
                    if ("object" == typeof t || M(t)) return t;
                    throw Mn("Can't set " + Rn(t) + " as a prototype")
                }(r), e ? t(n, r) : n.__proto__ = r, n
            }
        }() : void 0), kn = m(1..valueOf), Fn = "\t\n\v\f\r                　\u2028\u2029\ufeff", Dn = m("".replace),
        Ln = RegExp("^[" + Fn + "]+"), Gn = RegExp("(^|[^" + Fn + "])[" + Fn + "]+$"), Wn = function (t) {
            return function (e) {
                var n = Ke(A(e));
                return 1 & t && (n = Dn(n, Ln, "")), 2 & t && (n = Dn(n, Gn, "$1")), n
            }
        }, Un = {start: Wn(1), end: Wn(2), trim: Wn(3)}, Vn = we.f, Kn = It.f, zn = Ft.f, Jn = Un.trim, Bn = "Number",
        Yn = a[Bn];
    Tn[Bn];
    var $n = Yn.prototype, Xn = a.TypeError, Hn = m("".slice), Qn = m("".charCodeAt), qn = function (t) {
        var e, n, r, o, i, a, u, c, f = bt(t, "number");
        if (Y(f)) throw Xn("Cannot convert a Symbol value to a number");
        if ("string" == typeof f && f.length > 2) if (f = Jn(f), 43 === (e = Qn(f, 0)) || 45 === e) {
            if (88 === (n = Qn(f, 2)) || 120 === n) return NaN
        } else if (48 === e) {
            switch (Qn(f, 1)) {
                case 66:
                case 98:
                    r = 2, o = 49;
                    break;
                case 79:
                case 111:
                    r = 8, o = 55;
                    break;
                default:
                    return +f
            }
            for (a = (i = Hn(f, 2)).length, u = 0; u < a; u++) if ((c = Qn(i, u)) < 48 || c > o) return NaN;
            return parseInt(i, r)
        }
        return +f
    }, Zn = Re(Bn, !Yn(" 0o1") || !Yn("0b1") || Yn("+0x1")), tr = function (t) {
        var e, n = arguments.length < 1 ? 0 : Yn(function (t) {
            var e = bt(t, "number");
            return "bigint" == typeof e ? e : qn(e)
        }(t));
        return D($n, e = this) && u((function () {
            kn(e)
        })) ? function (t, e, n) {
            var r, o;
            return Cn && M(r = e.constructor) && r !== n && k(o = r.prototype) && o !== n.prototype && Cn(t, o), t
        }(Object(n), this, tr) : n
    };
    tr.prototype = $n, Zn && ($n.constructor = tr), Ce({
        global: !0,
        constructor: !0,
        wrap: !0,
        forced: Zn
    }, {Number: tr});
    Zn && function (t, e) {
        for (var n, r = c ? Vn(e) : "MAX_VALUE,MIN_VALUE,NaN,NEGATIVE_INFINITY,POSITIVE_INFINITY,EPSILON,MAX_SAFE_INTEGER,MIN_SAFE_INTEGER,isFinite,isInteger,isNaN,isSafeInteger,parseFloat,parseInt,fromString,range".split(","), o = 0; r.length > o; o++) ut(e, n = r[o]) && !ut(t, n) && zn(t, n, Kn(e, n))
    }(Tn[Bn], Yn);
    var er = Array.isArray || function (t) {
            return "Array" == O(t)
        }, nr = TypeError, rr = function (t) {
            if (t > 9007199254740991) throw nr("Maximum allowed index exceeded");
            return t
        }, or = function (t, e, n) {
            var r = mt(e);
            r in t ? Ft.f(t, r, y(0, n)) : t[r] = n
        }, ir = function () {
        }, ar = [], ur = F("Reflect", "construct"), cr = /^\s*(?:class|function)\b/, fr = m(cr.exec), lr = !cr.exec(ir),
        sr = function (t) {
            if (!M(t)) return !1;
            try {
                return ur(ir, ar, t), !0
            } catch (t) {
                return !1
            }
        }, pr = function (t) {
            if (!M(t)) return !1;
            switch (Ue(t)) {
                case"AsyncFunction":
                case"GeneratorFunction":
                case"AsyncGeneratorFunction":
                    return !1
            }
            try {
                return lr || !!fr(cr, Bt(t))
            } catch (t) {
                return !0
            }
        };
    pr.sham = !0;
    var gr = !ur || u((function () {
        var t;
        return sr(sr.call) || !sr(Object) || !sr((function () {
            t = !0
        })) || t
    })) ? pr : sr, vr = yt("species"), yr = Array, dr = function (t, e) {
        return new (function (t) {
            var e;
            return er(t) && (e = t.constructor, (gr(e) && (e === yr || er(e.prototype)) || k(e) && null === (e = e[vr])) && (e = void 0)), void 0 === e ? yr : e
        }(t))(0 === e ? 0 : e)
    }, hr = yt("species"), br = function (t) {
        return K >= 51 || !u((function () {
            var e = [];
            return (e.constructor = {})[hr] = function () {
                return {foo: 1}
            }, 1 !== e[t](Boolean).foo
        }))
    }, mr = yt("isConcatSpreadable"), Sr = K >= 51 || !u((function () {
        var t = [];
        return t[mr] = !1, t.concat()[0] !== t
    })), wr = function (t) {
        if (!k(t)) return !1;
        var e = t[mr];
        return void 0 !== e ? !!e : er(t)
    };
    Ce({target: "Array", proto: !0, arity: 1, forced: !Sr || !br("concat")}, {
        concat: function (t) {
            var e, n, r, o, i, a = it(this), u = dr(a, 0), c = 0;
            for (e = -1, r = arguments.length; e < r; e++) if (wr(i = -1 === e ? a : arguments[e])) for (o = ve(i), rr(c + o), n = 0; n < o; n++, c++) n in i && or(u, c, i[n]); else rr(c + 1), or(u, c++, i);
            return u.length = c, u
        }
    });
    var Or = m([].slice), xr = br("slice"), Er = yt("species"), Ir = Array, Nr = Math.max;
    Ce({target: "Array", proto: !0, forced: !xr}, {
        slice: function (t, e) {
            var n, r, o, i = P(this), a = ve(i), u = pe(t, a), c = pe(void 0 === e ? a : e, a);
            if (er(i) && (n = i.constructor, (gr(n) && (n === Ir || er(n.prototype)) || k(n) && null === (n = n[Er])) && (n = void 0), n === Ir || void 0 === n)) return Or(i, u, c);
            for (r = new (void 0 === n ? Ir : n)(Nr(c - u, 0)), o = 0; u < c; u++, o++) u in i && or(r, o, i[u]);
            return r.length = o, r
        }
    });
    var jr = De ? {}.toString : function () {
        return "[object " + Ue(this) + "]"
    };
    De || ie(Object.prototype, "toString", jr, {unsafe: !0});
    var Ar = RegExp.prototype, Pr = Ut.PROPER, _r = "toString", Tr = RegExp.prototype[_r], Rr = u((function () {
        return "/a/b" != Tr.call({source: "a", flags: "b"})
    })), Mr = Pr && Tr.name != _r;
    (Rr || Mr) && ie(RegExp.prototype, _r, (function () {
        var t = Pt(this), e = Ke(t.source), n = Ke(function (t) {
            var e = t.flags;
            return void 0 !== e || "flags" in Ar || ut(t, "flags") || !D(Ar, t) ? e : s(ze, t)
        }(t));
        return "/" + e + "/" + n
    }), {unsafe: !0});
    var Cr = function () {
        function e(t) {
            !function (t, e) {
                if (!(t instanceof e)) throw new TypeError("Cannot call a class as a function")
            }(this, e), this.install(t)
        }

        var n, r, o;
        return n = e, r = [{
            key: "install", value: function (t) {
                window.Action ? t() : document.addEventListener("ActionReady", t, !1)
            }
        }, {
            key: "closeCurrentPage", value: function () {
                var t = {clientFunName: "closeCurrentPage"};
                try {
                    Action.postMessage(JSON.stringify(t))
                } catch (e) {
                    console.log("GAMESDK error", "closeCurrentPage", JSON.stringify(t))
                }
            }
        }, {
            key: "parseUrlQuery", value: function () {
                var t = location.search, e = new Object;
                if (-1 !== t.indexOf("?")) for (var n = t.substr(1).split("&"), r = 0; r < n.length; r++) e[n[r].split("=")[0]] = decodeURIComponent(n[r].split("=")[1]);
                return e
            }
        }, {
            key: "toWalletPage", value: function (t) {
                var e = t.isTTChat, n = void 0 === e || e, r = t.isENV, o = void 0 === r || r, i = t.callback,
                    a = void 0 === i ? function () {
                    } : i, u = t.showDialog, c = void 0 === u || u, f = t.from_function, l = void 0 === f ? "" : f,
                    s = t.ext_state, p = void 0 === s ? "" : s, g = this.parseUrlQuery(),
                    v = 1 === this.versionStringCompare(g.version, "2.1.3");
                if (!n && v) if (c) this.openWalletDialog({callback: a, from_function: l, ext_state: p}); else try {
                    Router.postMessage("ttchat://money")
                } catch (t) {
                    console.log("GAMESDK error", "toWalletPage", "ttchat://money")
                } else this.toNextWebView({url: "/wallet/?page=wallet", isTTChat: n, isENV: o})
            }
        }, {
            key: "versionStringCompare", value: function () {
                for (var t = arguments.length > 1 && void 0 !== arguments[1] ? arguments[1] : "", e = (arguments.length > 0 && void 0 !== arguments[0] ? arguments[0] : "").split("."), n = t.split("."), r = Math.max(e.length, n.length), o = 0, i = 0; i < r; i++) {
                    var a = e.length > i ? e[i] : 0, u = isNaN(Number(a)) ? a.charCodeAt() : Number(a),
                        c = n.length > i ? n[i] : 0, f = isNaN(Number(c)) ? c.charCodeAt() : Number(c);
                    if (u < f) {
                        o = -1;
                        break
                    }
                    if (u > f) {
                        o = 1;
                        break
                    }
                }
                return o
            }
        }, {
            key: "openWalletDialog", value: function (t) {
                var e = t.callback, n = t.from_function, r = t.ext_state,
                    o = this.setRandomMethodName("walletDialogCb");
                window[o] = function (t) {
                    var n = decodeURIComponent(escape(window.atob(t))), r = JSON.parse(n);
                    e(r)
                };
                var i = {clientFunName: "purchaseFromApp", jsFunName: o, from_function: n, ext_state: r};
                try {
                    Action.postMessage(JSON.stringify(i))
                } catch (t) {
                    console.log("GAMESDK error", "purchaseFromApp", JSON.stringify(i))
                }
            }
        }, {
            key: "toNextWebView", value: function (t) {
                var e = t.url, n = t.isTTChat, r = void 0 === n || n, o = t.isENV, i = void 0 === o || o,
                    a = "".concat(r ? i ? "https://h5.testing.ttchat.com" : "https://app.ttchat.com" : i ? "https://kafu-app-testing.ttyuyin.com" : "https://app.kafumena.com").concat(e);
                try {
                    Router.postMessage(a)
                } catch (t) {
                    console.log("GAMESDK error", "toNextWebView", JSON.stringify(a))
                }
            }
        }, {
            key: "setRandomMethodName", value: function (t) {
                var e = Math.random().toString(36).slice(2);
                return "".concat(t, "_").concat(e)
            }
        }, {
            key: "gameLogin", value: function (t) {
                var e = t.cpId, n = t.gameId, r = t.callback, o = this.setRandomMethodName("gameLogin");
                window[o] = r;
                var i = {clientFunName: "gameLogin", cpId: e, gameId: n, jsFunName: o};
                try {
                    Action.postMessage(JSON.stringify(i))
                } catch (t) {
                    console.log("GAMESDK error", "gameLogin", JSON.stringify(i))
                }
            }
        }], r && t(n.prototype, r), o && t(n, o), Object.defineProperty(n, "prototype", {writable: !1}), e
    }();
    window.GAMESDK = Cr
}));
