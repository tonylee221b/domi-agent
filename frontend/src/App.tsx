import { useEffect, useState } from "react"
import {
  AlertTriangle,
  ArrowRight,
  BusFront,
  Check,
  ChevronLeft,
  ChevronRight,
  CreditCard,
  HeartHandshake,
  Hospital,
  Languages,
  LoaderCircle,
  Mic,
  Search,
  Sparkles,
  TrainFront,
} from "lucide-react"
import { Button } from "./components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./components/ui/card"
import { Input } from "./components/ui/input"
import { Progress } from "./components/ui/progress"

type BookingType = "HOSPITAL" | "TRANSPORT"
type Locale = "ko" | "en"
type Stage = "choose" | "request" | "preview" | "payment" | "reserving" | "complete"
type TransportMode = "TRAIN" | "INTERCITY_BUS" | "EXPRESS_BUS"
type DateChoice = "오늘" | "내일" | "모레"
type TimeChoice = "오전" | "오후"

type PreviewOption = {
  id: string
  title: string
  summary: string
  transportMode?: TransportMode
  serviceInfo: string
  origin: string
  destination: string
  departureTime: string
  arrivalTime: string
  details: { label: string; value: string }[]
  totalPrice: number
  warning?: string
}

type PreviewResponse = {
  type: BookingType
  options: PreviewOption[]
  searchId?: string
  page: number
  hasMore: boolean
}
type ReservationResponse = { message: string; reservationNumbers: string[] }
type QuestionResponse = { highlights: string[]; detail: string }

type SpeechRecognitionLike = {
  lang: string
  interimResults: boolean
  start: () => void
  onresult: (event: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void
  onerror: () => void
}

declare global {
  interface Window {
    SpeechRecognition?: new () => SpeechRecognitionLike
    webkitSpeechRecognition?: new () => SpeechRecognitionLike
  }
}

const transportLocations = ["서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "수원", "천안", "청주", "전주", "춘천", "강릉", "포항", "창원", "제주"]
const hospitalRegions = ["서울", "부산", "인천", "대구", "광주", "대전", "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"]
const departments = ["내과", "정형외과", "신경과", "외과", "안과", "이비인후과", "피부과", "재활의학과", "가정의학과", "치과"]

const en: Record<string, string> = {
  "도미": "Domi",
  "동행 도우미 AI": "AI companion",
  "도미 홈으로 이동": "Go to Domi home",
  "도움말": "Help",
  "화면의 큰 버튼을 따라 한 단계씩 진행해 주세요.": "Follow the large buttons one step at a time.",
  "안녕하세요, 무엇을 도와드릴까요?": "Hello, how can I help?",
  "오늘 필요한 도움을 하나만 골라주세요.": "Choose what you need today.",
  "복잡한 내용은 도미가 차근차근 여쭤볼게요.": "Domi will guide you one step at a time.",
  "예약이 필요하신가요?": "Do you need a reservation?",
  "병원 방문 도와주세요": "Book a hospital visit",
  "예약 가능한 진료 시간을 찾아드려요.": "Find an available appointment.",
  "교통편 찾아주세요": "Find transportation",
  "기차와 버스 시간을 알아봐 드려요.": "Find train and intercity bus schedules.",
  "궁금한 점도 물어보세요": "Ask a question",
  "병원 이용 방법, 열차 할인이나 환불 규정을 알려드려요.": "Ask about hospitals, discounts, refunds, or supported services.",
  "예: KTX 경로 할인은 주말에도 되나요?": "Example: Is the KTX senior discount available on weekends?",
  "병원과 교통 이용 안내 질문": "Hospital and transportation question",
  "찾는 중…": "Searching…",
  "물어보기": "Ask",
  "병원 예약": "Hospital booking",
  "어느 병원에 가고 싶으세요?": "Which hospital would you like to visit?",
  "교통편 찾기": "Transportation",
  "어디로 가고 싶으세요?": "Where would you like to go?",
  "아래에서 하나씩 고르거나 음성으로 말씀해 주세요.": "Choose each item below or use voice input.",
  "언제 가시나요?": "When are you going?",
  "오늘": "Today", "내일": "Tomorrow", "모레": "In two days",
  "오전인가요, 오후인가요?": "Morning or afternoon?",
  "오전": "Morning", "오후": "Afternoon",
  "출발지": "Origin", "출발지를 골라주세요": "Choose an origin",
  "병원 지역": "Hospital area", "목적지": "Destination",
  "병원 지역을 골라주세요": "Choose a hospital area", "목적지를 골라주세요": "Choose a destination",
  "진료과": "Department", "진료과를 골라주세요": "Choose a department",
  "말로 하셔도 돼요": "You can also speak",
  "목록에 없는 장소나 자세한 요청은 음성으로 말씀해 주세요.": "Use voice input for places or details not listed.",
  "음성으로 말하기": "Speak", "들은 내용": "What I heard", "선택 내용 사용하기": "Use selected options",
  "이전": "Back", "다음": "Next", "찾고 있어요…": "Searching…",
  "선택한 일정": "Selected schedule", "이 일정으로 예약할까요?": "Would you like to book this schedule?",
  "시간과 비용을 함께 확인해 주세요.": "Please check the time and price.", "선택지": "Options",
  "병원": "Hospital", "추천 옵션": "Recommended", "진료 시간": "Appointment time",
  "출발": "Departure", "도착": "Arrival", "다른 선택지 넘기기": "Browse other options",
  "이전 선택지": "Previous options", "다음 선택지": "Next options", "검색": "Search",
  "상세 정보": "Details", "총 결제 금액": "Total payment",
  "결제하기": "Pay", "결제 확인": "Payment confirmation",
  "결제할 내용을 확인해 주세요.": "Please review your payment.",
  "결제 버튼을 누르면 Mock 예약을 진행합니다.": "The demo reservation starts when you select Pay.",
  "결제 수단": "Payment method", "체험용 카드": "Demo card",
  "실제 카드 정보 입력이나 결제는 이루어지지 않습니다.": "No card information is entered and no real payment is made.",
  "결제하고 예약하기": "Pay and book", "예약 중…": "Booking…", "잠시만 기다려 주세요.": "Please wait a moment.",
  "예약 완료": "Booking complete", "준비가 모두 끝났어요.": "Everything is ready.", "예약번호": "Reservation number",
  "병원 예약이 완료됐어요.": "Your hospital booking is complete.", "교통편 예약이 완료됐어요.": "Your transportation booking is complete.",
  "병원까지 가는 교통편도 필요하신가요?": "Do you also need transportation to the hospital?",
  "기차와 버스 시간을 이어서 찾아드릴게요.": "Continue to find train and intercity bus schedules.",
  "교통편 알아보기": "Find transportation", "아니요, 홈으로": "No, go home", "홈으로": "Home",
  "교통편": "Transportation", "기차": "Train", "시외버스": "Intercity bus", "고속버스": "Express bus",
  "진료": "Appointment", "병원 주소": "Hospital address", "진료비": "Medical fee",
  "이용 날짜": "Travel date", "이동 구간": "Legs", "교통비": "Transportation fare",
  "서울": "Seoul", "부산": "Busan", "대구": "Daegu", "인천": "Incheon", "광주": "Gwangju",
  "대전": "Daejeon", "울산": "Ulsan", "세종": "Sejong", "수원": "Suwon", "천안": "Cheonan",
  "청주": "Cheongju", "전주": "Jeonju", "춘천": "Chuncheon", "강릉": "Gangneung", "포항": "Pohang",
  "창원": "Changwon", "제주": "Jeju", "경기": "Gyeonggi", "강원": "Gangwon",
  "충북": "North Chungcheong", "충남": "South Chungcheong", "전북": "North Jeolla",
  "전남": "South Jeolla", "경북": "North Gyeongsang", "경남": "South Gyeongsang",
  "내과": "Internal Medicine", "정형외과": "Orthopedics", "신경과": "Neurology", "외과": "Surgery",
  "안과": "Ophthalmology", "이비인후과": "ENT", "피부과": "Dermatology", "재활의학과": "Rehabilitation Medicine",
  "가정의학과": "Family Medicine", "치과": "Dentistry",
  "이 브라우저에서는 음성 입력을 지원하지 않습니다. 아래 선택 버튼을 이용해 주세요.": "Voice input is not supported in this browser. Please use the options below.",
  "말씀을 듣지 못했습니다. 다시 눌러 천천히 말씀해 주세요.": "I couldn't hear you. Please try again and speak slowly.",
  "궁금한 내용을 입력해 주세요.": "Enter your question.", "답을 찾지 못했습니다.": "I couldn't find an answer.",
  "답을 찾는 데 시간이 오래 걸리고 있습니다. 다시 시도해 주세요.": "The answer is taking too long. Please try again.",
  "잠시 후 다시 시도해 주세요.": "Please try again shortly.",
  "날짜, 시간, 병원 지역과 진료과를 모두 골라 주세요.": "Choose a date, time, hospital area, and department.",
  "날짜, 시간, 출발지와 목적지를 모두 골라 주세요.": "Choose a date, time, origin, and destination.",
  "출발지와 목적지는 다르게 골라 주세요.": "Origin and destination must be different.",
  "일정을 찾지 못했습니다.": "I couldn't find a schedule.",
  "조회가 오래 걸리고 있습니다. 잠시 후 다시 시도해 주세요.": "The search is taking too long. Please try again shortly.",
  "다른 병원을 찾지 못했습니다.": "I couldn't find another hospital.",
  "예약을 완료하지 못했습니다.": "The booking could not be completed.",
  "환승과 요금을 함께 확인한 일정입니다.": "This schedule includes transfer and fare checks.",
}

function translate(locale: Locale, value: string) {
  return locale === "en" ? en[value] ?? value : value
}

export default function App() {
  const [locale, setLocale] = useState<Locale>(() => localStorage.getItem("locale") === "en" ? "en" : "ko")
  const [stage, setStage] = useState<Stage>("choose")
  const [type, setType] = useState<BookingType>("HOSPITAL")
  const [message, setMessage] = useState("")
  const [dateChoice, setDateChoice] = useState<DateChoice>("오늘")
  const [timeChoice, setTimeChoice] = useState<TimeChoice>()
  const [origin, setOrigin] = useState("")
  const [destination, setDestination] = useState("")
  const [department, setDepartment] = useState("")
  const [preview, setPreview] = useState<PreviewResponse>()
  const [optionIndex, setOptionIndex] = useState(0)
  const [carouselPage, setCarouselPage] = useState(0)
  const [completed, setCompleted] = useState<ReservationResponse>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [question, setQuestion] = useState("")
  const [answer, setAnswer] = useState<QuestionResponse>()
  const [questionLoading, setQuestionLoading] = useState(false)
  const [questionError, setQuestionError] = useState("")
  const t = (value: string) => translate(locale, value)
  const money = new Intl.NumberFormat(locale === "ko" ? "ko-KR" : "en-US")
  const price = (value: number) => locale === "ko" ? `${money.format(value)}원` : `₩${money.format(value)}`
  const title = (value: string) => value.split(" · ").map(t).join(" · ")

  useEffect(() => {
    document.documentElement.lang = locale
    localStorage.setItem("locale", locale)
  }, [locale])

  const step = { choose: 1, request: 2, preview: 3, payment: 4, reserving: 4, complete: 5 }[stage]
  const option = preview?.options[optionIndex]
  const options = preview?.options ?? []
  const carouselPages = Math.ceil(options.length / 3)
  const visibleOptions = options.slice(carouselPage * 3, carouselPage * 3 + 3)

  function begin(nextType: BookingType, initialDestination = "") {
    setType(nextType)
    setMessage("")
    setDateChoice("오늘")
    setTimeChoice(undefined)
    setOrigin("")
    setDestination(initialDestination)
    setDepartment("")
    setError("")
    setStage("request")
  }

  function toggleLocale() {
    setLocale((current) => current === "ko" ? "en" : "ko")
    setError("")
    setQuestionError("")
    setAnswer(undefined)
  }

  function listen() {
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition
    if (!Recognition) {
      setError(t("이 브라우저에서는 음성 입력을 지원하지 않습니다. 아래 선택 버튼을 이용해 주세요."))
      return
    }
    const recognition = new Recognition()
    recognition.lang = locale === "ko" ? "ko-KR" : "en-US"
    recognition.interimResults = false
    recognition.onresult = (event) => {
      setMessage(event.results[0][0].transcript)
      setError("")
    }
    recognition.onerror = () => setError(t("말씀을 듣지 못했습니다. 다시 눌러 천천히 말씀해 주세요."))
    recognition.start()
  }

  async function askQuestion() {
    if (!question.trim()) {
      setQuestionError(t("궁금한 내용을 입력해 주세요."))
      return
    }
    setQuestionLoading(true)
    setQuestionError("")
    setAnswer(undefined)
    try {
      const response = await fetch("/api/bookings/question", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ question: question.trim() }),
        signal: AbortSignal.timeout(45_000),
      })
      const body: QuestionResponse & { message?: string } = await response.json()
      if (!response.ok) throw new Error(locale === "ko" ? body.message || t("답을 찾지 못했습니다.") : t("답을 찾지 못했습니다."))
      setAnswer(body)
    } catch (reason) {
      setQuestionError(reason instanceof DOMException && reason.name === "TimeoutError"
        ? t("답을 찾는 데 시간이 오래 걸리고 있습니다. 다시 시도해 주세요.")
        : reason instanceof Error ? reason.message : t("잠시 후 다시 시도해 주세요."))
    } finally {
      setQuestionLoading(false)
    }
  }

  async function requestPreview() {
    const selectedMessage = timeChoice && destination && (type === "HOSPITAL" ? department : origin)
      ? type === "TRANSPORT"
        ? `${dateChoice} ${timeChoice}에 ${origin}에서 ${destination}으로 가고 싶어.`
        : `${dateChoice} ${timeChoice}에 ${destination} 지역의 ${department} 진료를 받고 싶어.`
      : ""
    const requestMessage = message.trim() || selectedMessage
    if (!requestMessage) {
      setError(t(type === "HOSPITAL"
        ? "날짜, 시간, 병원 지역과 진료과를 모두 골라 주세요."
        : "날짜, 시간, 출발지와 목적지를 모두 골라 주세요."))
      return
    }
    if (!message.trim() && type === "TRANSPORT" && origin === destination) {
      setError(t("출발지와 목적지는 다르게 골라 주세요."))
      return
    }
    setLoading(true)
    setError("")
    try {
      const response = await fetch("/api/bookings/preview", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type, message: requestMessage }),
        signal: AbortSignal.timeout(45_000),
      })
      const body = await response.json()
      if (!response.ok) throw new Error(locale === "ko" ? body.message || t("일정을 찾지 못했습니다.") : t("일정을 찾지 못했습니다."))
      setPreview(body)
      setOptionIndex(0)
      setCarouselPage(0)
      setStage("preview")
    } catch (reason) {
      setError(reason instanceof DOMException && reason.name === "TimeoutError"
        ? t("조회가 오래 걸리고 있습니다. 잠시 후 다시 시도해 주세요.")
        : reason instanceof Error ? reason.message : t("잠시 후 다시 시도해 주세요."))
    } finally {
      setLoading(false)
    }
  }

  async function loadHospitalPage(page: number) {
    if (!preview?.searchId) return
    setLoading(true)
    setError("")
    try {
      const response = await fetch("/api/bookings/preview/hospital-page", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ searchId: preview.searchId, page }),
        signal: AbortSignal.timeout(45_000),
      })
      const body = await response.json()
      if (!response.ok) throw new Error(locale === "ko" ? body.message || t("다른 병원을 찾지 못했습니다.") : t("다른 병원을 찾지 못했습니다."))
      setPreview(body)
      setOptionIndex(0)
      setCarouselPage(page < preview.page ? Math.max(0, Math.ceil(body.options.length / 3) - 1) : 0)
    } catch (reason) {
      setError(reason instanceof DOMException && reason.name === "TimeoutError"
        ? t("조회가 오래 걸리고 있습니다. 잠시 후 다시 시도해 주세요.")
        : reason instanceof Error ? reason.message : t("잠시 후 다시 시도해 주세요."))
    } finally {
      setLoading(false)
    }
  }

  function previousOptions() {
    if (carouselPage > 0) setCarouselPage((page) => page - 1)
    else if (preview?.type === "HOSPITAL" && preview.page > 1) loadHospitalPage(preview.page - 1)
  }

  function nextOptions() {
    if (carouselPage < carouselPages - 1) setCarouselPage((page) => page + 1)
    else if (preview?.type === "HOSPITAL" && preview.hasMore) loadHospitalPage(preview.page + 1)
  }

  async function approve() {
    if (!option) return
    setLoading(true)
    setError("")
    setStage("reserving")
    try {
      const [response] = await Promise.all([
        fetch("/api/bookings/approve", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ previewId: option.id, approved: true }),
        }),
        new Promise((resolve) => setTimeout(resolve, 1_200)),
      ])
      const body = await response.json()
      if (!response.ok) throw new Error(locale === "ko" ? body.message || t("예약을 완료하지 못했습니다.") : t("예약을 완료하지 못했습니다."))
      setCompleted(body)
      setStage("complete")
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : t("잠시 후 다시 시도해 주세요."))
      setStage("payment")
    } finally {
      setLoading(false)
    }
  }

  function reset() {
    setStage("choose")
    setPreview(undefined)
    setCompleted(undefined)
    setError("")
  }

  function goBack() {
    if (stage === "payment") {
      setStage("preview")
      setError("")
      return
    }
    if (stage === "preview") {
      setStage("request")
      setError("")
      return
    }
    reset()
  }

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b bg-card">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-5 py-5 sm:px-8">
          <button className="flex items-center gap-3 rounded-xl text-left outline-none focus-visible:ring-4 focus-visible:ring-ring/40 disabled:opacity-50" onClick={reset} disabled={stage === "reserving"} aria-label={t("도미 홈으로 이동")}>
            <span className="flex size-12 items-center justify-center rounded-xl bg-primary text-primary-foreground">
              <HeartHandshake aria-hidden="true" />
            </span>
            <div>
              <div className="text-2xl font-medium">{t("도미")}</div>
              <div className="text-sm text-muted-foreground">{t("동행 도우미 AI")}</div>
            </div>
          </button>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={toggleLocale} aria-label={locale === "ko" ? "Switch to English" : "한국어로 변경"}>
              <Languages aria-hidden="true" />{locale === "ko" ? "EN" : "한국어"}
            </Button>
            <Button variant="outline" size="sm" onClick={() => alert(t("화면의 큰 버튼을 따라 한 단계씩 진행해 주세요."))}>{t("도움말")}</Button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-5 py-10 sm:px-8 sm:py-14">
        <div className="mb-9 flex items-center gap-4 text-base text-muted-foreground" aria-label={locale === "ko" ? `전체 5단계 중 ${step}단계` : `Step ${step} of 5`}>
          <span className="shrink-0">{step} / 5</span>
          <Progress value={step * 20} />
        </div>

        {stage === "choose" && (
          <section>
            <p className="mb-3 text-lg font-medium text-primary">{t("안녕하세요, 무엇을 도와드릴까요?")}</p>
            <h1 className="text-4xl font-medium leading-tight tracking-tight sm:text-5xl">{t("오늘 필요한 도움을 하나만 골라주세요.")}</h1>
            <p className="mt-5 text-xl leading-relaxed text-muted-foreground">{t("복잡한 내용은 도미가 차근차근 여쭤볼게요.")}</p>
            <h2 className="mb-5 mt-9 text-2xl font-medium">{t("예약이 필요하신가요?")}</h2>
            <div className="grid gap-5 sm:grid-cols-2">
              <Choice icon={<Hospital />} title={t("병원 방문 도와주세요")} description={t("예약 가능한 진료 시간을 찾아드려요.")} onClick={() => begin("HOSPITAL")} />
              <Choice icon={<TrainFront />} title={t("교통편 찾아주세요")} description={t("기차와 버스 시간을 알아봐 드려요.")} onClick={() => begin("TRANSPORT")} />
            </div>
            <Card className="mt-9 border-primary/40 bg-secondary/30">
              <CardHeader className="pb-4">
                <CardTitle>{t("궁금한 점도 물어보세요")}</CardTitle>
                <CardDescription>{t("병원 이용 방법, 열차 할인이나 환불 규정을 알려드려요.")}</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="flex flex-col gap-3 sm:flex-row">
                  <Input value={question} onChange={(event) => setQuestion(event.target.value)} onKeyDown={(event) => event.key === "Enter" && askQuestion()} placeholder={t("예: KTX 경로 할인은 주말에도 되나요?")} aria-label={t("병원과 교통 이용 안내 질문")} />
                  <Button className="shrink-0" onClick={askQuestion} disabled={questionLoading}><Search aria-hidden="true" />{t(questionLoading ? "찾는 중…" : "물어보기")}</Button>
                </div>
                <ErrorMessage message={questionError} />
                {answer && (
                  <div className="mt-5 rounded-xl border bg-card p-6" aria-live="polite">
                    <ul className="list-disc space-y-3 pl-7 text-2xl font-medium leading-snug marker:text-primary">
                      {answer.highlights.map((highlight) => <li key={highlight}>{highlight}</li>)}
                    </ul>
                    <p className="mt-5 border-t pt-5 text-lg leading-relaxed text-muted-foreground">{answer.detail}</p>
                  </div>
                )}
              </CardContent>
            </Card>
          </section>
        )}

        {stage === "request" && (
          <section>
            <p className="mb-3 text-lg font-medium text-primary">{t(type === "HOSPITAL" ? "병원 예약" : "교통편 찾기")}</p>
            <h1 className="text-4xl font-medium leading-tight tracking-tight sm:text-5xl">{t(type === "HOSPITAL" ? "어느 병원에 가고 싶으세요?" : "어디로 가고 싶으세요?")}</h1>
            <p className="mb-8 mt-5 text-xl text-muted-foreground">{t("아래에서 하나씩 고르거나 음성으로 말씀해 주세요.")}</p>

            <Card>
              <CardContent className="space-y-8 pt-7">
                <fieldset>
                  <legend className="mb-4 text-2xl font-medium">{t("언제 가시나요?")}</legend>
                  <div className="grid grid-cols-3 gap-3">
                    {(["오늘", "내일", "모레"] as DateChoice[]).map((value) => (
                      <SelectionCard key={value} selected={dateChoice === value} onClick={() => setDateChoice(value)}>{t(value)}</SelectionCard>
                    ))}
                  </div>
                </fieldset>

                <fieldset>
                  <legend className="mb-4 text-2xl font-medium">{t("오전인가요, 오후인가요?")}</legend>
                  <div className="grid grid-cols-2 gap-4">
                    {(["오전", "오후"] as TimeChoice[]).map((value) => (
                      <SelectionCard key={value} selected={timeChoice === value} onClick={() => setTimeChoice(value)}>{t(value)}</SelectionCard>
                    ))}
                  </div>
                </fieldset>

                <div className={`grid gap-5 ${type === "TRANSPORT" ? "sm:grid-cols-2" : ""}`}>
                  {type === "TRANSPORT" && (
                    <label className="text-xl font-medium">
                      {t("출발지")}
                      <select className="mt-3 h-14 w-full rounded-xl border-2 bg-card px-4 text-lg outline-none focus-visible:ring-4 focus-visible:ring-ring/40" value={origin} onChange={(event) => setOrigin(event.target.value)}>
                        <option value="">{t("출발지를 골라주세요")}</option>
                        {transportLocations.map((location) => <option key={location} value={location}>{t(location)}</option>)}
                      </select>
                    </label>
                  )}
                  <label className="text-xl font-medium">
                    {t(type === "HOSPITAL" ? "병원 지역" : "목적지")}
                    <select className="mt-3 h-14 w-full rounded-xl border-2 bg-card px-4 text-lg outline-none focus-visible:ring-4 focus-visible:ring-ring/40" value={destination} onChange={(event) => setDestination(event.target.value)}>
                      <option value="">{t(type === "HOSPITAL" ? "병원 지역을 골라주세요" : "목적지를 골라주세요")}</option>
                      {(type === "HOSPITAL" ? hospitalRegions : transportLocations).map((location) => <option key={location} value={location}>{t(location)}</option>)}
                    </select>
                  </label>
                </div>

                {type === "HOSPITAL" && (
                  <label className="block text-xl font-medium">
                    {t("진료과")}
                    <select className="mt-3 h-14 w-full rounded-xl border-2 bg-card px-4 text-lg outline-none focus-visible:ring-4 focus-visible:ring-ring/40" value={department} onChange={(event) => setDepartment(event.target.value)}>
                      <option value="">{t("진료과를 골라주세요")}</option>
                      {departments.map((value) => <option key={value} value={value}>{t(value)}</option>)}
                    </select>
                  </label>
                )}
              </CardContent>
            </Card>

            <div className="mt-7 rounded-2xl border-2 border-primary/30 bg-secondary/30 p-6">
              <h2 className="text-2xl font-medium">{t("말로 하셔도 돼요")}</h2>
              <p className="mt-2 text-lg text-muted-foreground">{t("목록에 없는 장소나 자세한 요청은 음성으로 말씀해 주세요.")}</p>
              <Button className="mt-5" size="lg" variant="outline" onClick={listen}><Mic aria-hidden="true" />{t("음성으로 말하기")}</Button>
              {message && (
                <div className="mt-5 rounded-xl bg-card p-5" aria-live="polite">
                  <div className="text-base text-muted-foreground">{t("들은 내용")}</div>
                  <p className="mt-2 text-xl font-medium">{message}</p>
                  <Button className="mt-4" size="sm" variant="outline" onClick={() => setMessage("")}>{t("선택 내용 사용하기")}</Button>
                </div>
              )}
            </div>
            <ErrorMessage message={error} />
            <div className="mt-8 flex flex-col-reverse gap-3 sm:flex-row sm:justify-between">
              <Button variant="outline" onClick={goBack}><ChevronLeft aria-hidden="true" />{t("이전")}</Button>
              <Button onClick={requestPreview} disabled={loading}>{t(loading ? "찾고 있어요…" : "다음")}</Button>
            </div>
          </section>
        )}

        {stage === "preview" && option && (
          <section>
            <p className="mb-3 text-lg font-medium text-primary">{t("선택한 일정")}</p>
            <h1 className="text-4xl font-medium leading-tight tracking-tight sm:text-5xl">{t("이 일정으로 예약할까요?")}</h1>
            <p className="mb-8 mt-5 text-xl leading-relaxed text-muted-foreground">{t("시간과 비용을 함께 확인해 주세요.")}</p>
            {options.length > 0 && (
              <div className="mb-12">
                <h2 className="mb-4 text-2xl font-medium">{t("선택지")}</h2>
                <div className="grid gap-4 md:grid-cols-3">
                  {visibleOptions.map((candidate, index) => {
                    const absoluteIndex = carouselPage * 3 + index
                    const { label, icon: Icon, color } = candidate.transportMode
                      ? transportMeta[candidate.transportMode]
                      : { label: t("병원"), icon: Hospital, color: "bg-secondary text-primary" }
                    return (
                      <Card className={`relative h-full cursor-pointer outline-none focus-visible:ring-4 focus-visible:ring-ring/40 ${preview.page === 1 && absoluteIndex === 0 ? "border-[3px] border-primary" : optionIndex === absoluteIndex ? "border-success" : "hover:border-primary/60"} ${optionIndex === absoluteIndex ? "ring-4 ring-success/25" : ""}`} key={candidate.id} onClick={() => setOptionIndex(absoluteIndex)} onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault()
                          setOptionIndex(absoluteIndex)
                        }
                      }} role="button" tabIndex={0} aria-pressed={optionIndex === absoluteIndex}>
                          {optionIndex === absoluteIndex && <span className="absolute right-4 top-4 flex size-9 items-center justify-center rounded-full bg-success text-white"><Check aria-hidden="true" className="size-5" /></span>}
                          <CardHeader className="gap-4 p-6 pr-16">
                          {preview.page === 1 && absoluteIndex === 0 && <span className="inline-flex items-center gap-2 text-lg font-medium text-primary"><Sparkles aria-hidden="true" className="size-5" />{t("추천 옵션")}</span>}
                            <div className="flex items-center gap-4">
                              <span className={`flex size-12 shrink-0 items-center justify-center rounded-xl ${color}`}><Icon aria-hidden="true" className="size-6" /></span>
                              <div>
                                <div className="text-lg font-medium text-muted-foreground">{preview.type === "HOSPITAL" ? title(candidate.title) : t(label)}</div>
                                {candidate.serviceInfo && <div className="mt-1 text-lg font-medium text-foreground">{candidate.serviceInfo}</div>}
                              </div>
                            </div>
                            {preview.type === "HOSPITAL" ? (
                              <div className="border-t pt-4">
                                <div className="text-base text-muted-foreground">{t("진료 시간")}</div>
                                <CardTitle className="mt-1 text-xl">{candidate.serviceInfo}</CardTitle>
                                <div className="mt-3 text-base leading-relaxed text-muted-foreground">{candidate.destination}</div>
                              </div>
                            ) : (
                              <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3 border-t pt-4">
                                <div>
                                  <div className="text-base text-muted-foreground">{t("출발")}</div>
                                  <CardTitle className="mt-1 text-xl">{candidate.origin}</CardTitle>
                                  <div className="mt-2 text-lg font-medium text-primary">{candidate.departureTime}</div>
                                </div>
                                <ArrowRight className="size-6 shrink-0 text-primary" aria-hidden="true" />
                                <div className="text-right">
                                  <div className="text-base text-muted-foreground">{t("도착")}</div>
                                  <CardTitle className="mt-1 text-xl">{candidate.destination}</CardTitle>
                                  <div className="mt-2 text-lg font-medium text-primary">{candidate.arrivalTime}</div>
                                </div>
                              </div>
                            )}
                            {candidate.warning && <p className="text-lg text-warning">{candidate.warning}</p>}
                            <strong className="pt-2 text-2xl font-medium">{price(candidate.totalPrice)}</strong>
                          </CardHeader>
                      </Card>
                    )
                  })}
                </div>
                {(carouselPages > 1 || preview.page > 1 || preview.hasMore) && (
                  <div className="mt-5 flex items-center justify-center gap-4" aria-label={t("다른 선택지 넘기기")}>
                    <Button size="icon" variant="outline" className="size-12" onClick={previousOptions} disabled={loading || (carouselPage === 0 && preview.page === 1)} aria-label={t("이전 선택지")}><ChevronLeft aria-hidden="true" /></Button>
                    <span className="text-lg text-muted-foreground">{preview.type === "HOSPITAL" && `${t("검색")} ${preview.page} · `}{carouselPage + 1} / {carouselPages}</span>
                    <Button size="icon" variant="outline" className="size-12" onClick={nextOptions} disabled={loading || (carouselPage === carouselPages - 1 && !preview.hasMore)} aria-label={t("다음 선택지")}><ChevronRight aria-hidden="true" /></Button>
                  </div>
                )}
              </div>
            )}
            <h2 className="mb-5 text-2xl font-medium">{t("상세 정보")}</h2>
            {option.warning && (
              <div className="mb-5 flex gap-3 rounded-xl bg-warning-background p-5 text-lg text-warning" role="alert">
                <AlertTriangle className="mt-1 shrink-0" aria-hidden="true" />
                <span>{option.warning}</span>
              </div>
            )}
            <Card className="border-primary" aria-live="polite">
              <CardHeader>
                <CardTitle>{title(option.title)}</CardTitle>
                <CardDescription>{locale === "en" && preview.type === "HOSPITAL" ? `Hospital hours: ${option.summary.replace("병원 운영시간: ", "")}` : t(option.summary)}</CardDescription>
                {option.transportMode && <TransportSchedule option={option} t={t} />}
              </CardHeader>
              <CardContent>
                <dl className="border-t pt-5 text-lg">
                  {option.details.map((detail) => (
                    <div className="grid gap-1 py-2 sm:grid-cols-[11rem_1fr]" key={detail.label}>
                      <dt className="text-muted-foreground">{t(detail.label)}</dt>
                      <dd className="font-medium">{detail.value}</dd>
                    </div>
                  ))}
                  <div className="mt-3 grid gap-1 border-t pt-5 sm:grid-cols-[11rem_1fr]">
                    <dt className="text-muted-foreground">{t("총 결제 금액")}</dt>
                    <dd className="text-2xl font-medium">{price(option.totalPrice)}</dd>
                  </div>
                </dl>
              </CardContent>
            </Card>
            <ErrorMessage message={error} />
            <div className="mt-7 flex flex-col-reverse gap-3 sm:flex-row sm:justify-between">
              <Button variant="outline" onClick={goBack}><ChevronLeft aria-hidden="true" />{t("이전")}</Button>
              <Button size="lg" onClick={() => setStage("payment")}>{price(option.totalPrice)} {t("결제하기")}</Button>
            </div>
          </section>
        )}

        {stage === "payment" && option && (
          <section>
            <p className="mb-3 text-lg font-medium text-primary">{t("결제 확인")}</p>
            <h1 className="text-4xl font-medium leading-tight tracking-tight sm:text-5xl">{t("결제할 내용을 확인해 주세요.")}</h1>
            <p className="mb-8 mt-5 text-xl text-muted-foreground">{t("결제 버튼을 누르면 Mock 예약을 진행합니다.")}</p>
            <Card>
              <CardHeader>
                <CardTitle>{title(option.title)}</CardTitle>
                <CardDescription>{option.serviceInfo}</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="flex items-center gap-4 rounded-xl bg-secondary p-5">
                  <span className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-primary text-white"><CreditCard aria-hidden="true" /></span>
                  <div>
                    <div className="text-base text-muted-foreground">{t("결제 수단")}</div>
                    <div className="text-xl font-medium">{t("체험용 카드")}</div>
                  </div>
                </div>
                <div className="mt-6 flex items-end justify-between border-t pt-6">
                  <span className="text-xl text-muted-foreground">{t("총 결제 금액")}</span>
                  <strong className="text-3xl font-medium">{price(option.totalPrice)}</strong>
                </div>
                <p className="mt-5 rounded-xl bg-warning-background p-4 text-lg text-warning">{t("실제 카드 정보 입력이나 결제는 이루어지지 않습니다.")}</p>
              </CardContent>
            </Card>
            <ErrorMessage message={error} />
            <div className="mt-7 flex flex-col-reverse gap-3 sm:flex-row sm:justify-between">
              <Button variant="outline" onClick={goBack}><ChevronLeft aria-hidden="true" />{t("이전")}</Button>
              <Button size="lg" onClick={approve} disabled={loading}>{price(option.totalPrice)} {t("결제하고 예약하기")}</Button>
            </div>
          </section>
        )}

        {stage === "reserving" && (
          <section className="py-16 text-center" aria-live="assertive">
            <LoaderCircle className="mx-auto size-16 animate-spin text-primary" aria-hidden="true" />
            <h1 className="mt-7 text-4xl font-medium sm:text-5xl">{t("예약 중…")}</h1>
            <p className="mt-4 text-xl text-muted-foreground">{t("잠시만 기다려 주세요.")}</p>
          </section>
        )}

        {stage === "complete" && completed && (
          <section className="text-center">
            <span className="mx-auto mb-6 flex size-20 items-center justify-center rounded-full bg-success text-white"><Check className="size-10" aria-hidden="true" /></span>
            <p className="mb-3 text-lg font-medium text-success">{t("예약 완료")}</p>
            <h1 className="text-4xl font-medium leading-tight tracking-tight sm:text-5xl">{t("준비가 모두 끝났어요.")}</h1>
            <p className="mt-5 text-xl text-muted-foreground">{locale === "ko" ? completed.message : t(preview?.type === "HOSPITAL" ? "병원 예약이 완료됐어요." : "교통편 예약이 완료됐어요.")}</p>
            <div className="mx-auto mt-8 max-w-md rounded-xl bg-success-background p-6">
              <div className="mb-2 text-base text-muted-foreground">{t("예약번호")}</div>
              {completed.reservationNumbers.map((number) => <div className="text-xl font-medium tracking-wide" key={number}>{number}</div>)}
            </div>
            {preview?.type === "HOSPITAL" && (
              <Card className="mx-auto mt-8 max-w-md text-left">
                <CardHeader>
                  <CardTitle>{t("병원까지 가는 교통편도 필요하신가요?")}</CardTitle>
                  <CardDescription>{t("기차와 버스 시간을 이어서 찾아드릴게요.")}</CardDescription>
                </CardHeader>
                <CardContent>
                  <Button className="w-full" size="lg" onClick={() => begin("TRANSPORT", destination)}>{t("교통편 알아보기")}</Button>
                </CardContent>
              </Card>
            )}
            <Button className="mt-6" size="lg" variant="outline" onClick={goBack}>{t(preview?.type === "HOSPITAL" ? "아니요, 홈으로" : "홈으로")}</Button>
          </section>
        )}
      </main>
    </div>
  )
}

const transportMeta = {
  TRAIN: { label: "기차", icon: TrainFront, color: "bg-secondary text-primary" },
  INTERCITY_BUS: { label: "시외버스", icon: BusFront, color: "bg-warning-background text-warning" },
  EXPRESS_BUS: { label: "고속버스", icon: BusFront, color: "bg-warning-background text-warning" },
} satisfies Record<TransportMode, { label: string; icon: typeof TrainFront; color: string }>

function TransportSchedule({ option, t }: { option: PreviewOption; t: (value: string) => string }) {
  if (!option.transportMode) return null
  const { label, icon: Icon, color } = transportMeta[option.transportMode]
  return (
    <div className="mt-3 rounded-xl bg-muted/60 p-4">
      <div className="flex items-center gap-3">
        <span className={`flex size-11 shrink-0 items-center justify-center rounded-xl ${color}`}><Icon aria-hidden="true" className="size-6" /></span>
        <div>
          <div className="text-sm text-muted-foreground">{t("교통편")}</div>
          <strong className="text-lg font-medium">{t(label)}</strong>
          {option.serviceInfo && <div className="mt-1 text-base font-medium text-primary">{option.serviceInfo}</div>}
        </div>
      </div>
      <div className="mt-4 grid grid-cols-[1fr_auto_1fr] items-center gap-3 border-t pt-4">
        <div>
          <div className="text-sm text-muted-foreground">{t("출발")}</div>
          <strong className="mt-1 block text-xl font-medium">{option.origin}</strong>
          <span className="mt-2 block text-lg font-medium text-primary">{option.departureTime}</span>
        </div>
        <ArrowRight className="size-6 text-primary" aria-hidden="true" />
        <div className="text-right">
          <div className="text-sm text-muted-foreground">{t("도착")}</div>
          <strong className="mt-1 block text-xl font-medium">{option.destination}</strong>
          <span className="mt-2 block text-lg font-medium text-primary">{option.arrivalTime}</span>
        </div>
      </div>
    </div>
  )
}

function Choice({ icon, title, description, onClick }: { icon: React.ReactNode; title: string; description: string; onClick: () => void }) {
  return (
    <button className="rounded-2xl border-2 border-border bg-card p-7 text-left outline-none transition-colors hover:border-primary focus-visible:ring-4 focus-visible:ring-ring/40" onClick={onClick}>
      <span className="mb-5 flex size-13 items-center justify-center rounded-xl bg-secondary text-primary [&_svg]:size-6">{icon}</span>
      <strong className="block text-2xl font-medium">{title}</strong>
      <span className="mt-2 block text-lg leading-relaxed text-muted-foreground">{description}</span>
    </button>
  )
}

function SelectionCard({ children, selected, onClick }: { children: React.ReactNode; selected: boolean; onClick: () => void }) {
  return (
    <button type="button" className={`relative min-h-20 rounded-2xl border-2 px-4 text-xl font-medium outline-none focus-visible:ring-4 focus-visible:ring-ring/40 ${selected ? "border-primary bg-secondary text-primary" : "bg-card hover:border-primary/60"}`} onClick={onClick} aria-pressed={selected}>
      {selected && <Check className="absolute right-3 top-3 size-5" aria-hidden="true" />}
      {children}
    </button>
  )
}

function ErrorMessage({ message }: { message: string }) {
  return message ? <p className="mt-5 text-lg text-destructive" role="alert">{message}</p> : null
}
